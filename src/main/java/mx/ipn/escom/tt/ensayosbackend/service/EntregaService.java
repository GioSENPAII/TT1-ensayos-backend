package mx.ipn.escom.tt.ensayosbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.dto.AjusteCalificacionRequest;
import mx.ipn.escom.tt.ensayosbackend.dto.AlumnoInscritoResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.EntregaResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.ReporteResponse;
import mx.ipn.escom.tt.ensayosbackend.entity.*;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.ia.MotorIaClient;
import mx.ipn.escom.tt.ensayosbackend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entregas de ensayos y su calificación (CU-ALU-02, CU-ALU-03, CU-ALU-04, CU-WEB-02). */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntregaService {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // RN-IA-02
    private static final BigDecimal DIEZ = BigDecimal.TEN;
    private static final String MSG_ERROR_IA =
            "No se pudo calificar tu ensayo porque el motor de calificación no está disponible. "
                    + "Intenta enviarlo de nuevo en unos minutos.";

    private final EnsayoRepository ensayoRepository;
    private final CalificacionRepository calificacionRepository;
    private final AuditoriaModificacionRepository auditoriaRepository;
    private final TareaRepository tareaRepository;
    private final InscripcionRepository inscripcionRepository;
    private final GrupoService grupoService;
    private final PdfService pdfService;
    private final AlmacenamientoService almacenamiento;
    private final MotorIaClient motorIa;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    /** La alerta de plagio se activa si la primera coincidencia del motor supera este valor. */
    @Value("${app.ia.umbral-plagio:0.96}")
    private BigDecimal umbralPlagio;

    // ---------------------------------------------------------------- Enviar ensayo (CU-ALU-02)

    /**
     * 1) valida y guarda la entrega como EN_REVISION; 2) llama al motor de IA fuera de transacción;
     * 3) guarda la calificación (CALIFICADO / POSIBLE_PLAGIO) o marca ERROR.
     * Por ahora es síncrono; la corrección C6 lo volverá asíncrono con 202 + consulta periódica.
     */
    public EntregaResponse enviar(Usuario alumno, Long idTarea, MultipartFile archivo) {
        byte[] contenido = leerYValidar(archivo);
        String nombreArchivo = nombreSeguro(archivo.getOriginalFilename());
        String texto = pdfService.extraerTexto(contenido);

        Long idEnsayo = tx.execute(status -> registrarEntrega(alumno, idTarea, nombreArchivo, contenido, texto));

        try {
            ObjectNode resultado = motorIa.calificar(contenido, nombreArchivo, idEnsayo, idTarea);
            tx.executeWithoutResult(status -> guardarCalificacion(idEnsayo, resultado));
        } catch (MotorIaClient.MotorIaException | IllegalArgumentException e) {
            log.warn("Entrega {} sin calificar: {}", idEnsayo, e.getMessage());
            tx.executeWithoutResult(status -> ensayoRepository.findById(idEnsayo)
                    .ifPresent(en -> en.setEstado(Ensayo.Estado.ERROR)));
        }
        return tx.execute(status -> {
            Ensayo ensayo = ensayoRepository.findById(idEnsayo).orElseThrow();
            return detalleResponse(ensayo, false);
        });
    }

    private byte[] leerYValidar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo PDF");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el límite de 10 MB");
        }
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("No se pudo leer el archivo");
        }
        if (!nombre.toLowerCase(Locale.ROOT).endsWith(".pdf") || !pdfService.esPdf(contenido)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se permiten archivos PDF");
        }
        return contenido;
    }

    private Long registrarEntrega(Usuario alumno, Long idTarea, String nombreArchivo, byte[] contenido, String texto) {
        Tarea tarea = tareaRepository.findById(idTarea)
                .orElseThrow(() -> ApiException.notFound("La tarea no existe"));
        // RNF-06: solo alumnos inscritos en el grupo de la tarea
        if (!inscripcionRepository.existsByAlumnoAndGrupo(alumno, tarea.getGrupo())) {
            throw ApiException.forbidden("No estás inscrito en el grupo de esta tarea");
        }
        switch (tarea.disponibilidad(LocalDateTime.now())) {
            case PROXIMA -> throw ApiException.conflict("Esta tarea aún no está abierta para entregas");
            case CERRADA -> throw ApiException.conflict("El plazo de entrega de esta tarea ya cerró"); // RN-WEB-02
            case ABIERTA -> { }
        }

        Ensayo ensayo = ensayoRepository.findByAlumnoAndTarea(alumno, tarea).orElse(null);
        if (ensayo != null) {
            switch (ensayo.getEstado()) {
                // RN-WEB-04: un ensayo evaluado es de solo lectura
                case CALIFICADO, POSIBLE_PLAGIO -> throw ApiException.conflict(
                        "Tu ensayo ya fue calificado y no puede reemplazarse");
                case EN_REVISION -> throw ApiException.conflict(
                        "Tu ensayo se está calificando. Espera el resultado.");
                // ERROR: el motor falló; se permite reintentar sin consumir un intento (CU-ALU-02 E3)
                case ERROR -> almacenamiento.eliminar(ensayo.getRutaArchivo());
            }
        } else {
            ensayo = Ensayo.builder().alumno(alumno).tarea(tarea).build();
        }

        ensayo.setNombreArchivo(nombreArchivo);
        ensayo.setRutaArchivo(almacenamiento.guardar(contenido, tarea.getIdTarea(), alumno.getIdUsuario()));
        ensayo.setTamanoBytes(contenido.length);
        ensayo.setTextoExtraido(texto);
        ensayo.setEstado(Ensayo.Estado.EN_REVISION);
        ensayo.setFechaEntrega(LocalDateTime.now());
        return ensayoRepository.save(ensayo).getIdEnsayo();
    }

    private void guardarCalificacion(Long idEnsayo, ObjectNode resultado) {
        Ensayo ensayo = ensayoRepository.findById(idEnsayo).orElseThrow();
        JsonNode metadata = resultado.path("metadata");
        JsonNode desglose = resultado.path("desglose_rubrica");
        if (!metadata.path("calificacion_final").isNumber() || !desglose.isArray() || desglose.isEmpty()) {
            throw new IllegalArgumentException("Respuesta del motor de IA incompleta");
        }

        boolean plagio = esPlagio(resultado);
        BigDecimal similitud = null;
        for (JsonNode c : resultado.path("plagio").path("coincidencias")) {
            BigDecimal s = c.path("similarity").decimalValue();
            if (similitud == null || s.compareTo(similitud) > 0) {
                similitud = s;
            }
        }

        Calificacion calificacion = calificacionRepository.findByEnsayo(ensayo)
                .orElseGet(() -> Calificacion.builder().ensayo(ensayo).build());
        // RN-IA-04: 0.0-10.0 redondeada a un decimal
        calificacion.setCalificacionFinal(limitar(metadata.path("calificacion_final").decimalValue()));
        calificacion.setSimilitudPlagio(similitud == null ? null : similitud.setScale(4, RoundingMode.HALF_UP));
        calificacion.setReporteJson(resultado.toString());
        calificacion.setModificadoPorDocente(false);
        calificacion.setFechaEvaluacion(LocalDateTime.now());
        calificacion.setFechaModificacion(null);
        calificacionRepository.save(calificacion);

        // RN-IA-03: la alerta de plagio no anula la calificación, solo cambia el estado
        ensayo.setEstado(plagio ? Ensayo.Estado.POSIBLE_PLAGIO : Ensayo.Estado.CALIFICADO);
    }

    // ---------------------------------------------------------------- Consultas

    @Transactional(readOnly = true)
    public EntregaResponse detalle(Usuario usuario, Long idEnsayo) {
        Ensayo ensayo = ensayoRepository.findById(idEnsayo)
                .orElseThrow(() -> ApiException.notFound("La entrega no existe"));
        return detalleResponse(ensayo, verificarAcceso(usuario, ensayo));
    }

    @Transactional(readOnly = true)
    public ReporteResponse reporte(Usuario usuario, Long idEnsayo) {
        EntregaResponse detalle = detalle(usuario, idEnsayo);
        if (detalle.getReporte() == null) {
            throw ApiException.notFound("Esta entrega aún no tiene calificación");
        }
        return detalle.getReporte();
    }

    /** CU-ALU-04: historial del alumno, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<EntregaResponse> historial(Usuario alumno) {
        List<Ensayo> ensayos = ensayoRepository.findByAlumnoOrderByFechaEntregaDesc(alumno);
        Map<Long, Calificacion> califs = calificacionesPorEnsayo(ensayos);
        return ensayos.stream().map(e -> resumen(e, califs.get(e.getIdEnsayo()), false)).toList();
    }

    /** CU-WEB-02: entregas de una tarea del profesor. */
    @Transactional(readOnly = true)
    public List<EntregaResponse> entregasDeTarea(Usuario profesor, Long idTarea) {
        Tarea tarea = tareaRepository.findById(idTarea)
                .orElseThrow(() -> ApiException.notFound("La tarea no existe"));
        grupoService.grupoDelProfesor(profesor, tarea.getGrupo().getIdGrupo());
        List<Ensayo> ensayos = ensayoRepository.findByTareaOrderByFechaEntregaDesc(tarea);
        Map<Long, Calificacion> califs = calificacionesPorEnsayo(ensayos);
        return ensayos.stream().map(e -> resumen(e, califs.get(e.getIdEnsayo()), true)).toList();
    }

    // ---------------------------------------------------------------- Ajuste del profesor (CU-WEB-02)

    @Transactional
    public ReporteResponse ajustar(Usuario profesor, Long idEnsayo, AjusteCalificacionRequest request) {
        Ensayo ensayo = ensayoRepository.findById(idEnsayo)
                .orElseThrow(() -> ApiException.notFound("La entrega no existe"));
        grupoService.grupoDelProfesor(profesor, ensayo.getTarea().getGrupo().getIdGrupo());
        Calificacion calificacion = calificacionRepository.findByEnsayo(ensayo)
                .orElseThrow(() -> ApiException.conflict("Esta entrega aún no tiene calificación"));

        ObjectNode reporte = leerReporte(calificacion);
        ObjectNode criterio = null;
        for (JsonNode c : reporte.path("desglose_rubrica")) {
            if (c.path("criterio").asText().equalsIgnoreCase(request.getCriterio().trim())) {
                criterio = (ObjectNode) c;
            }
        }
        if (criterio == null) {
            throw ApiException.badRequest("El criterio \"" + request.getCriterio() + "\" no existe en esta rúbrica");
        }

        BigDecimal maximo = criterio.path("puntaje_maximo").decimalValue();
        BigDecimal nuevo = request.getPuntaje().setScale(2, RoundingMode.HALF_UP);
        if (nuevo.signum() < 0 || nuevo.compareTo(maximo) > 0) {
            throw ApiException.badRequest("El puntaje de \"" + criterio.path("criterio").asText()
                    + "\" debe estar entre 0 y " + maximo.stripTrailingZeros().toPlainString()); // E1
        }

        BigDecimal anterior = criterio.path("puntaje_obtenido").decimalValue();
        if (!criterio.has("puntaje_ia")) {
            criterio.put("puntaje_ia", anterior); // se conserva el valor original de la IA (RF-PRO-07)
        }
        criterio.put("puntaje_obtenido", nuevo);
        criterio.put("modificado_por_docente", true);

        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode c : reporte.path("desglose_rubrica")) {
            suma = suma.add(c.path("puntaje_obtenido").decimalValue());
        }
        BigDecimal finalNueva = limitar(suma);
        ((ObjectNode) reporte.path("metadata")).put("calificacion_final", finalNueva);

        calificacion.setReporteJson(reporte.toString());
        calificacion.setCalificacionFinal(finalNueva);
        calificacion.setModificadoPorDocente(true);
        calificacion.setFechaModificacion(LocalDateTime.now());
        calificacionRepository.save(calificacion);

        auditoriaRepository.save(AuditoriaModificacion.builder()
                .calificacion(calificacion)
                .profesor(profesor)
                .criterioModificado(criterio.path("criterio").asText())
                .valorOriginal(anterior)
                .valorAjustado(nuevo)
                .build());

        return aReporte(calificacion, true);
    }

    // ---------------------------------------------------------------- Auxiliares

    /** Devuelve true si quien consulta es el profesor del grupo. RN-WEB-03: nadie más ve la entrega. */
    private boolean verificarAcceso(Usuario usuario, Ensayo ensayo) {
        switch (usuario.getRol()) {
            case ALUMNO -> {
                if (!ensayo.getAlumno().getIdUsuario().equals(usuario.getIdUsuario())) {
                    throw ApiException.forbidden("No tienes acceso a esta entrega");
                }
                return false;
            }
            case PROFESOR -> {
                grupoService.grupoDelProfesor(usuario, ensayo.getTarea().getGrupo().getIdGrupo());
                return true;
            }
            default -> throw ApiException.forbidden("No tienes permiso para realizar esta acción.");
        }
    }

    private Map<Long, Calificacion> calificacionesPorEnsayo(List<Ensayo> ensayos) {
        if (ensayos.isEmpty()) {
            return Map.of();
        }
        return calificacionRepository.findByEnsayoIn(ensayos).stream()
                .collect(Collectors.toMap(c -> c.getEnsayo().getIdEnsayo(), Function.identity()));
    }

    private EntregaResponse detalleResponse(Ensayo ensayo, boolean vistaProfesor) {
        Calificacion calificacion = calificacionRepository.findByEnsayo(ensayo).orElse(null);
        EntregaResponse r = resumen(ensayo, calificacion, vistaProfesor);
        if (calificacion != null) {
            r.setReporte(aReporte(calificacion, vistaProfesor));
        }
        return r;
    }

    private EntregaResponse resumen(Ensayo e, Calificacion c, boolean vistaProfesor) {
        Tarea t = e.getTarea();
        EntregaResponse.EntregaResponseBuilder b = EntregaResponse.builder()
                .id(e.getIdEnsayo())
                .nombreArchivo(e.getNombreArchivo())
                .tamanoBytes(e.getTamanoBytes())
                .fechaEntrega(e.getFechaEntrega())
                .tareaId(t.getIdTarea())
                .tarea(t.getNombre())
                .grupoId(t.getGrupo().getIdGrupo())
                .grupo(t.getGrupo().getNombre())
                .estado(e.getEstado().name());
        if (c != null) {
            b.calificacionFinal(c.getCalificacionFinal().setScale(1, RoundingMode.HALF_UP))
                    .modificadoPorDocente(c.isModificadoPorDocente());
        }
        if (e.getEstado() == Ensayo.Estado.ERROR) {
            b.mensaje(MSG_ERROR_IA);
        }
        if (vistaProfesor) {
            Usuario a = e.getAlumno();
            b.alumno(AlumnoInscritoResponse.builder()
                    .id(a.getIdUsuario()).nombre(a.getNombre()).apellidos(a.getApellidos()).correo(a.getCorreo())
                    .build());
        }
        return b.build();
    }

    private ReporteResponse aReporte(Calificacion calificacion, boolean vistaProfesor) {
        ObjectNode reporte = leerReporte(calificacion);
        JsonNode banderas = reporte.path("banderas_retroalimentacion");
        boolean plagio = esPlagio(reporte);

        List<ReporteResponse.Criterio> criterios = new ArrayList<>();
        BigDecimal maxima = BigDecimal.ZERO;
        for (JsonNode c : reporte.path("desglose_rubrica")) {
            BigDecimal max = c.path("puntaje_maximo").decimalValue();
            BigDecimal obtenido = c.path("puntaje_obtenido").decimalValue();
            maxima = maxima.add(max);
            String nombre = c.path("criterio").asText();
            String detalleMotor = c.path("detalles").asText(null);
            RubricaInstitucional.Nivel nivel = RubricaInstitucional.nivel(obtenido, max);
            String descripcion = RubricaInstitucional.descripcion(nombre, nivel);
            criterios.add(ReporteResponse.Criterio.builder()
                    .criterio(nombre)
                    .puntajeObtenido(obtenido)
                    .puntajeMaximo(max)
                    .nivel(nivel.name())
                    .detalles(descripcion != null ? descripcion : detalleMotor)
                    .detallesMotor(detalleMotor)
                    .modificadoPorDocente(c.path("modificado_por_docente").asBoolean(false))
                    .puntajeIa(c.has("puntaje_ia") ? c.path("puntaje_ia").decimalValue() : null)
                    .build());
        }

        ReporteResponse.ReporteResponseBuilder b = ReporteResponse.builder()
                .calificacionFinal(calificacion.getCalificacionFinal().setScale(1, RoundingMode.HALF_UP))
                .calificacionMaxima(maxima.setScale(1, RoundingMode.HALF_UP))
                .observacion(observacion(reporte, plagio))
                .fechaEvaluacion(calificacion.getFechaEvaluacion())
                .modificadoPorDocente(calificacion.isModificadoPorDocente())
                .fechaModificacion(calificacion.getFechaModificacion())
                .posiblePlagio(plagio)
                .banderas(ReporteResponse.Banderas.builder()
                        .requiereRevisionDocente(requiereRevision(reporte, plagio))
                        .faltaContextoIntro(banderas.path("falta_contexto_intro").asBoolean(false))
                        .abusoVinetas(banderas.path("abuso_vinetas").asBoolean(false))
                        .build())
                .criterios(criterios);

        if (vistaProfesor) {
            List<ReporteResponse.Coincidencia> coincidencias = new ArrayList<>();
            for (JsonNode c : reporte.path("plagio").path("coincidencias")) {
                coincidencias.add(new ReporteResponse.Coincidencia(
                        c.path("document_hash").asText(), c.path("similarity").decimalValue()));
            }
            b.similitudMaxima(calificacion.getSimilitudPlagio()).coincidencias(coincidencias);
        }
        return b.build();
    }

    /**
     * Alerta de plagio: la similitud de la primera coincidencia devuelta por el motor debe ser mayor
     * al umbral (0.96). La bandera "detectado" del motor, que usa 0.92, no se toma en cuenta.
     */
    private boolean esPlagio(JsonNode reporte) {
        JsonNode coincidencias = reporte.path("plagio").path("coincidencias");
        if (!coincidencias.isArray() || coincidencias.isEmpty()) {
            return false;
        }
        return coincidencias.get(0).path("similarity").decimalValue().compareTo(umbralPlagio) > 0;
    }

    private static boolean motorMarcoPlagio(JsonNode reporte) {
        return reporte.path("plagio").path("detectado").asBoolean(false)
                || reporte.path("banderas_retroalimentacion").path("plagio_detectado").asBoolean(false);
    }

    /** Si el motor avisó plagio pero no supera el umbral, su observación sobre similitud ya no aplica. */
    private static String observacion(JsonNode reporte, boolean plagio) {
        String texto = reporte.path("metadata").path("observacion").asText(null);
        if (!plagio && motorMarcoPlagio(reporte)) {
            return null;
        }
        return texto;
    }

    /** La revisión docente que pidió el motor por plagio se descarta si no supera el umbral. */
    private static boolean requiereRevision(JsonNode reporte, boolean plagio) {
        boolean pedida = reporte.path("banderas_retroalimentacion").path("requiere_revision_docente").asBoolean(false);
        return plagio || (pedida && !motorMarcoPlagio(reporte));
    }

    private ObjectNode leerReporte(Calificacion calificacion) {
        try {
            JsonNode n = objectMapper.readTree(calificacion.getReporteJson());
            if (!n.path("desglose_rubrica").isArray()) {
                ((ObjectNode) n).set("desglose_rubrica", objectMapper.createArrayNode());
            }
            return (ObjectNode) n;
        } catch (IOException e) {
            throw new IllegalStateException("reporte_json inválido en calificación " + calificacion.getIdCalificacion(), e);
        }
    }

    private static BigDecimal limitar(BigDecimal valor) {
        BigDecimal v = valor.max(BigDecimal.ZERO).min(DIEZ);
        return v.setScale(1, RoundingMode.HALF_UP);
    }

    /** Solo el nombre (sin rutas del cliente) y a lo más 255 caracteres (ensayos.nombre_archivo). */
    private static String nombreSeguro(String original) {
        String nombre = original == null ? "ensayo.pdf" : original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).strip();
        if (nombre.isEmpty()) {
            nombre = "ensayo.pdf";
        }
        return nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre;
    }
}
