package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.TareaRequest;
import mx.ipn.escom.tt.ensayosbackend.dto.TareaResponse;
import mx.ipn.escom.tt.ensayosbackend.entity.Ensayo;
import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Tarea;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.repository.EnsayoRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.GrupoRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.InscripcionRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tareas de entrega (CU-WEB-05). El alumno solo las ve a partir de su fecha de apertura. */
@Service
@RequiredArgsConstructor
public class TareaService {

    private final TareaRepository tareaRepository;
    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final EnsayoRepository ensayoRepository;
    private final GrupoService grupoService;
    private final AlmacenamientoService almacenamiento;

    @Transactional
    public TareaResponse crear(Usuario profesor, TareaRequest request) {
        if (request.getGroupId() == null) {
            throw ApiException.badRequest("El grupo es obligatorio");
        }
        Grupo grupo = grupoService.grupoDelProfesor(profesor, request.getGroupId());
        String nombre = request.getNombre().trim();
        validarFechas(request);
        if (tareaRepository.existsByGrupoAndNombreIgnoreCase(grupo, nombre)) {
            throw ApiException.conflict("Ya existe una tarea con ese nombre en este grupo");
        }
        Tarea tarea = tareaRepository.save(Tarea.builder()
                .grupo(grupo)
                .nombre(nombre)
                .fechaApertura(request.getFechaApertura())
                .fechaCierre(request.getFechaCierre())
                .build());
        return aResponseProfesor(tarea, false);
    }

    @Transactional(readOnly = true)
    public List<TareaResponse> listar(Usuario usuario, Long idGrupo) {
        Grupo grupo = grupoRepository.findById(idGrupo)
                .orElseThrow(() -> ApiException.notFound("El grupo no existe"));

        if (usuario.getRol() == Usuario.Rol.PROFESOR) {
            grupoService.grupoDelProfesor(usuario, idGrupo);
            return tareaRepository.findByGrupoOrderByFechaCierreAsc(grupo).stream()
                    .map(t -> aResponseProfesor(t, ensayoRepository.existsByTarea(t)))
                    .toList();
        }

        if (usuario.getRol() == Usuario.Rol.ALUMNO) {
            if (!inscripcionRepository.existsByAlumnoAndGrupo(usuario, grupo)) {
                throw ApiException.forbidden("No estás inscrito en este grupo");
            }
            LocalDateTime ahora = LocalDateTime.now();
            List<Tarea> tareas = tareaRepository
                    .findByGrupoAndFechaAperturaLessThanEqualOrderByFechaCierreAsc(grupo, ahora);
            Map<Long, Ensayo> entregas = ensayoRepository.findByAlumnoAndTareaIn(usuario, tareas).stream()
                    .collect(Collectors.toMap(e -> e.getTarea().getIdTarea(), Function.identity()));
            return tareas.stream()
                    .map(t -> aResponseAlumno(t, entregas.get(t.getIdTarea()), ahora))
                    .toList();
        }

        // RN-WEB-03: el administrador no accede a tareas ni entregas
        throw ApiException.forbidden("No tienes permiso para realizar esta acción.");
    }

    @Transactional
    public TareaResponse actualizar(Usuario profesor, Long idTarea, TareaRequest request) {
        Tarea tarea = tareaDelProfesor(profesor, idTarea);
        String nombre = request.getNombre().trim();
        validarFechas(request);

        boolean tieneEntregas = ensayoRepository.existsByTarea(tarea);
        if (tieneEntregas) {
            // CU-WEB-05 A2/E3: con entregas solo se permite extender la fecha de cierre
            boolean cambiaBloqueados = !tarea.getNombre().equals(nombre)
                    || !tarea.getFechaApertura().equals(request.getFechaApertura());
            if (cambiaBloqueados) {
                throw ApiException.conflict(
                        "La tarea ya tiene entregas: solo puedes extender la fecha de cierre. "
                                + "El nombre y la fecha de apertura no se pueden modificar.");
            }
            if (request.getFechaCierre().isBefore(tarea.getFechaCierre())) {
                throw ApiException.conflict(
                        "La tarea ya tiene entregas: la fecha de cierre solo puede extenderse, no adelantarse.");
            }
        } else if (tareaRepository.existsByGrupoAndNombreIgnoreCaseAndIdTareaNot(tarea.getGrupo(), nombre, idTarea)) {
            throw ApiException.conflict("Ya existe una tarea con ese nombre en este grupo");
        }

        tarea.setNombre(nombre);
        tarea.setFechaApertura(request.getFechaApertura());
        tarea.setFechaCierre(request.getFechaCierre());
        return aResponseProfesor(tareaRepository.save(tarea), tieneEntregas);
    }

    /** CU-WEB-05 B4: las entregas y calificaciones de la tarea caen por cascada. */
    @Transactional
    public void eliminar(Usuario profesor, Long idTarea) {
        Tarea tarea = tareaDelProfesor(profesor, idTarea);
        almacenamiento.eliminarAlConfirmar(ensayoRepository.rutasDeTarea(tarea));
        tareaRepository.delete(tarea);
    }

    // ---------------------------------------------------------------- Auxiliares

    private Tarea tareaDelProfesor(Usuario profesor, Long idTarea) {
        Tarea tarea = tareaRepository.findById(idTarea)
                .orElseThrow(() -> ApiException.notFound("La tarea no existe"));
        grupoService.grupoDelProfesor(profesor, tarea.getGrupo().getIdGrupo());
        return tarea;
    }

    /** Trunca a segundos (DATETIME no guarda fracciones) y valida que el cierre sea posterior a la apertura. */
    private static void validarFechas(TareaRequest request) {
        request.setFechaApertura(request.getFechaApertura().truncatedTo(ChronoUnit.SECONDS));
        request.setFechaCierre(request.getFechaCierre().truncatedTo(ChronoUnit.SECONDS));
        if (!request.getFechaCierre().isAfter(request.getFechaApertura())) {
            throw ApiException.badRequest("La fecha de cierre debe ser posterior a la fecha de apertura");
        }
    }

    private static TareaResponse.TareaResponseBuilder base(Tarea tarea, LocalDateTime ahora) {
        return TareaResponse.builder()
                .id(tarea.getIdTarea())
                .groupId(tarea.getGrupo().getIdGrupo())
                .grupo(tarea.getGrupo().getNombre())
                .nombre(tarea.getNombre())
                .fechaApertura(tarea.getFechaApertura())
                .fechaCierre(tarea.getFechaCierre())
                .disponibilidad(tarea.disponibilidad(ahora).name());
    }

    private static TareaResponse aResponseProfesor(Tarea tarea, boolean tieneEntregas) {
        return base(tarea, LocalDateTime.now()).tieneEntregas(tieneEntregas).build();
    }

    private static TareaResponse aResponseAlumno(Tarea tarea, Ensayo entrega, LocalDateTime ahora) {
        TareaResponse.EntregaResumen resumen = entrega == null ? null : TareaResponse.EntregaResumen.builder()
                .id(entrega.getIdEnsayo())
                .estado(entrega.getEstado().name())
                .fechaEntrega(entrega.getFechaEntrega())
                .build();
        return base(tarea, ahora)
                .entrega(resumen)
                .pendiente(entrega == null && tarea.disponibilidad(ahora) == Tarea.Disponibilidad.ABIERTA)
                .build();
    }
}
