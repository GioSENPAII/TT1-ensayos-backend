package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.*;
import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Inscripcion;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.repository.EnsayoRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.GrupoRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.InscripcionRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.TareaRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GrupoService {

    // Sin 0/O ni 1/I para que el código no se confunda al dictarlo o copiarlo
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LONGITUD_CODIGO = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final TareaRepository tareaRepository;
    private final EnsayoRepository ensayoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlmacenamientoService almacenamiento;

    // ---------------------------------------------------------------- Profesor (CU-WEB-01, CU-WEB-04)

    @Transactional
    public GrupoResponse crear(Usuario profesor, CrearGrupoRequest request) {
        String nombre = request.getNombre().trim();
        if (grupoRepository.existsByProfesorAndNombreIgnoreCase(profesor, nombre)) {
            throw ApiException.conflict("Ya tienes un grupo con ese nombre");
        }
        Grupo grupo = grupoRepository.save(Grupo.builder()
                .profesor(profesor)
                .nombre(nombre)
                .codigoAcceso(generarCodigoUnico())
                .build());
        return aResponse(grupo);
    }

    @Transactional(readOnly = true)
    public List<GrupoResponse> listarDelProfesor(Usuario profesor) {
        return grupoRepository.findByProfesorOrderByFechaCreacionDesc(profesor).stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GrupoResponse obtener(Usuario profesor, Long idGrupo) {
        return aResponse(grupoDelProfesor(profesor, idGrupo));
    }

    /** Elimina el grupo; tareas, inscripciones, entregas y calificaciones caen por cascada (CU-WEB-01 B4). */
    @Transactional
    public void eliminar(Usuario profesor, Long idGrupo) {
        Grupo grupo = grupoDelProfesor(profesor, idGrupo);
        almacenamiento.eliminarAlConfirmar(ensayoRepository.rutasDeGrupo(grupo));
        grupoRepository.delete(grupo);
    }

    /** RN-WEB-01: desactivar el código impide nuevas inscripciones sin afectar las existentes. */
    @Transactional
    public GrupoResponse cambiarEstado(Usuario profesor, Long idGrupo, EstadoGrupoRequest request) {
        Grupo grupo = grupoDelProfesor(profesor, idGrupo);
        grupo.setEstado(request.getEstado());
        return aResponse(grupoRepository.save(grupo));
    }

    @Transactional(readOnly = true)
    public List<AlumnoInscritoResponse> alumnos(Usuario profesor, Long idGrupo) {
        Grupo grupo = grupoDelProfesor(profesor, idGrupo);
        return inscripcionRepository.findAlumnosDelGrupo(grupo).stream()
                .map(i -> AlumnoInscritoResponse.builder()
                        .id(i.getAlumno().getIdUsuario())
                        .nombre(i.getAlumno().getNombre())
                        .apellidos(i.getAlumno().getApellidos())
                        .correo(i.getAlumno().getCorreo())
                        .fechaInscripcion(i.getFechaInscripcion())
                        .build())
                .toList();
    }

    /** RF-PRO-04: remueve al alumno y sus entregas en este grupo, sin tocar su cuenta ni otros grupos. */
    @Transactional
    public void removerAlumno(Usuario profesor, Long idGrupo, Long idAlumno) {
        Grupo grupo = grupoDelProfesor(profesor, idGrupo);
        Usuario alumno = usuarioRepository.findById(idAlumno)
                .orElseThrow(() -> ApiException.notFound("El alumno no pertenece a este grupo"));
        Inscripcion inscripcion = inscripcionRepository.findByAlumnoAndGrupo(alumno, grupo)
                .orElseThrow(() -> ApiException.notFound("El alumno no pertenece a este grupo"));
        almacenamiento.eliminarAlConfirmar(ensayoRepository.rutasDeAlumnoEnGrupo(alumno, grupo));
        ensayoRepository.deleteByAlumnoAndGrupo(alumno, grupo);
        inscripcionRepository.delete(inscripcion);
    }

    // ---------------------------------------------------------------- Alumno (CU-ALU-01)

    @Transactional(readOnly = true)
    public List<GrupoAlumnoResponse> misGrupos(Usuario alumno) {
        return inscripcionRepository.findByAlumnoOrderByFechaInscripcionDesc(alumno).stream()
                .map(this::aAlumnoResponse)
                .toList();
    }

    @Transactional
    public GrupoAlumnoResponse unirse(Usuario alumno, UnirseGrupoRequest request) {
        Grupo grupo = grupoRepository.findByCodigoAcceso(request.getCodigo().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound(
                        "Código de acceso no válido. Verifica el código con tu profesor"));
        if (grupo.getEstado() == Grupo.Estado.INACTIVO) {
            throw ApiException.conflict("Este grupo ya no acepta nuevos integrantes");
        }
        if (inscripcionRepository.existsByAlumnoAndGrupo(alumno, grupo)) {
            throw ApiException.conflict("Ya eres parte de este grupo");
        }
        Inscripcion inscripcion = inscripcionRepository.save(Inscripcion.builder()
                .alumno(alumno)
                .grupo(grupo)
                .build());
        return aAlumnoResponse(inscripcion);
    }

    // ---------------------------------------------------------------- Auxiliares

    /** CU-WEB-04 E3: un profesor solo accede a los grupos que creó (403). */
    Grupo grupoDelProfesor(Usuario profesor, Long idGrupo) {
        Grupo grupo = grupoRepository.findById(idGrupo)
                .orElseThrow(() -> ApiException.notFound("El grupo no existe"));
        if (!grupo.getProfesor().getIdUsuario().equals(profesor.getIdUsuario())) {
            throw ApiException.forbidden("No tienes acceso a este grupo");
        }
        return grupo;
    }

    private String generarCodigoUnico() {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder(LONGITUD_CODIGO);
            for (int i = 0; i < LONGITUD_CODIGO; i++) {
                sb.append(ALFABETO_CODIGO.charAt(RANDOM.nextInt(ALFABETO_CODIGO.length())));
            }
            codigo = sb.toString();
        } while (grupoRepository.existsByCodigoAcceso(codigo));
        return codigo;
    }

    private GrupoResponse aResponse(Grupo grupo) {
        return GrupoResponse.builder()
                .id(grupo.getIdGrupo())
                .nombre(grupo.getNombre())
                .codigoAcceso(grupo.getCodigoAcceso())
                .estado(grupo.getEstado().name())
                .fechaCreacion(grupo.getFechaCreacion())
                .totalAlumnos(inscripcionRepository.countByGrupo(grupo))
                .totalTareas(tareaRepository.countByGrupo(grupo))
                .build();
    }

    private GrupoAlumnoResponse aAlumnoResponse(Inscripcion inscripcion) {
        Grupo grupo = inscripcion.getGrupo();
        Usuario profesor = grupo.getProfesor();
        return GrupoAlumnoResponse.builder()
                .id(grupo.getIdGrupo())
                .nombre(grupo.getNombre())
                .profesor(profesor.getNombre() + " " + profesor.getApellidos())
                .estado(grupo.getEstado().name())
                .fechaInscripcion(inscripcion.getFechaInscripcion())
                .build();
    }
}
