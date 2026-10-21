package mx.ipn.escom.tt.ensayosbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.RendimientoGrupoResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.ResumenRendimientoResponse;
import mx.ipn.escom.tt.ensayosbackend.entity.Calificacion;
import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.repository.CalificacionRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.GrupoRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.InscripcionRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Estadísticas de calificaciones por grupo para las gráficas del profesor. */
@Service
@RequiredArgsConstructor
public class RendimientoService {

    // Límites inferiores de los rangos del histograma; el último incluye el 10
    private static final double[] RANGOS = {0, 6, 7, 8, 9, 10};
    private static final String[] ETIQUETAS = {"0–5.9", "6–6.9", "7–7.9", "8–8.9", "9–10"};

    private final GrupoService grupoService;
    private final GrupoRepository grupoRepository;
    private final TareaRepository tareaRepository;
    private final InscripcionRepository inscripcionRepository;
    private final CalificacionRepository calificacionRepository;
    private final ObjectMapper objectMapper;

    /** Promedio de cada grupo del profesor, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<ResumenRendimientoResponse> resumen(Usuario profesor) {
        return grupoRepository.findByProfesorOrderByFechaCreacionDesc(profesor).stream()
                .map(g -> {
                    List<BigDecimal> notas = notas(calificacionRepository.findDelGrupo(g));
                    return new ResumenRendimientoResponse(g.getIdGrupo(), g.getNombre(),
                            inscripcionRepository.countByGrupo(g), notas.size(), promedio(notas));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RendimientoGrupoResponse detalle(Usuario profesor, Long idGrupo) {
        Grupo grupo = grupoService.grupoDelProfesor(profesor, idGrupo);
        List<Calificacion> califs = calificacionRepository.findDelGrupo(grupo);
        List<BigDecimal> notas = notas(califs);

        List<RendimientoGrupoResponse.PorTarea> tareas = new ArrayList<>();
        Map<Long, List<Calificacion>> porTarea = califs.stream()
                .collect(Collectors.groupingBy(c -> c.getEnsayo().getTarea().getIdTarea()));
        tareaRepository.findByGrupoOrderByFechaCierreAsc(grupo).forEach(t -> {
            List<BigDecimal> n = notas(porTarea.getOrDefault(t.getIdTarea(), List.of()));
            tareas.add(new RendimientoGrupoResponse.PorTarea(t.getIdTarea(), t.getNombre(), t.getFechaCierre(),
                    n.size(), promedio(n), redondear(n.stream().min(BigDecimal::compareTo).orElse(null)),
                    redondear(n.stream().max(BigDecimal::compareTo).orElse(null))));
        });

        Map<Long, List<Calificacion>> porAlumno = califs.stream()
                .collect(Collectors.groupingBy(c -> c.getEnsayo().getAlumno().getIdUsuario()));
        List<RendimientoGrupoResponse.PorAlumno> alumnos = inscripcionRepository.findAlumnosDelGrupo(grupo).stream()
                .map(i -> {
                    Usuario a = i.getAlumno();
                    List<BigDecimal> n = notas(porAlumno.getOrDefault(a.getIdUsuario(), List.of()));
                    return new RendimientoGrupoResponse.PorAlumno(a.getIdUsuario(), a.getNombre(),
                            a.getApellidos(), n.size(), promedio(n));
                })
                .toList();

        return new RendimientoGrupoResponse(grupo.getIdGrupo(), grupo.getNombre(), alumnos.size(), notas.size(),
                promedio(notas), distribucion(notas), tareas, alumnos, criterios(califs));
    }

    private static List<RendimientoGrupoResponse.Rango> distribucion(List<BigDecimal> notas) {
        List<RendimientoGrupoResponse.Rango> rangos = new ArrayList<>();
        for (int i = 0; i < ETIQUETAS.length; i++) {
            double desde = RANGOS[i];
            double hasta = RANGOS[i + 1];
            boolean ultimo = i == ETIQUETAS.length - 1;
            long entregas = notas.stream().mapToDouble(BigDecimal::doubleValue)
                    .filter(v -> v >= desde && (ultimo ? v <= hasta : v < hasta))
                    .count();
            rangos.add(new RendimientoGrupoResponse.Rango(ETIQUETAS[i], BigDecimal.valueOf(desde),
                    BigDecimal.valueOf(hasta), entregas));
        }
        return rangos;
    }

    /** Promedio de cada criterio del desglose de la rúbrica (reporte_json.desglose_rubrica). */
    private List<RendimientoGrupoResponse.PorCriterio> criterios(List<Calificacion> califs) {
        Map<String, BigDecimal[]> sumas = new LinkedHashMap<>(); // criterio → {obtenido, máximo, n}
        for (Calificacion c : califs) {
            for (JsonNode criterio : leer(c).path("desglose_rubrica")) {
                BigDecimal[] s = sumas.computeIfAbsent(criterio.path("criterio").asText(),
                        k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                s[0] = s[0].add(criterio.path("puntaje_obtenido").decimalValue());
                s[1] = s[1].add(criterio.path("puntaje_maximo").decimalValue());
                s[2] = s[2].add(BigDecimal.ONE);
            }
        }
        return sumas.entrySet().stream()
                .map(e -> {
                    BigDecimal[] s = e.getValue();
                    BigDecimal porcentaje = s[1].signum() == 0 ? null
                            : s[0].multiply(BigDecimal.valueOf(100)).divide(s[1], 1, RoundingMode.HALF_UP);
                    return new RendimientoGrupoResponse.PorCriterio(e.getKey(),
                            s[0].divide(s[2], 2, RoundingMode.HALF_UP), s[1].divide(s[2], 2, RoundingMode.HALF_UP),
                            porcentaje);
                })
                .toList();
    }

    private JsonNode leer(Calificacion c) {
        try {
            return objectMapper.readTree(c.getReporteJson());
        } catch (IOException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static List<BigDecimal> notas(List<Calificacion> califs) {
        return califs.stream().map(Calificacion::getCalificacionFinal).toList();
    }

    private static BigDecimal promedio(List<BigDecimal> notas) {
        if (notas.isEmpty()) {
            return null;
        }
        return notas.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(notas.size()), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor == null ? null : valor.setScale(1, RoundingMode.HALF_UP);
    }
}
