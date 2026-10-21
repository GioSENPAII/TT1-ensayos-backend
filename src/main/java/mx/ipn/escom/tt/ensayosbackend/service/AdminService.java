package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.dto.CuentaResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.EntregaResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.EstadoCuentaRequest;
import mx.ipn.escom.tt.ensayosbackend.dto.MetricasResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.PaginaResponse;
import mx.ipn.escom.tt.ensayosbackend.entity.Ensayo;
import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Gestión del ciclo de vida de cuentas (CU-WEB-03, RF-ADM-01 a 03). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<Usuario.Rol> GESTIONABLES = EnumSet.of(Usuario.Rol.ALUMNO, Usuario.Rol.PROFESOR);
    private static final int TAMANO_MAXIMO = 100;

    private final UsuarioRepository usuarioRepository;
    private final GrupoRepository grupoRepository;
    private final TareaRepository tareaRepository;
    private final InscripcionRepository inscripcionRepository;
    private final EnsayoRepository ensayoRepository;
    private final TokenService tokenService;
    private final AlmacenamientoService almacenamiento;
    private final EntregaService entregaService;

    /** Directorio de alumnos y profesores, con filtros por rol, estado y texto (correo o nombre). */
    @Transactional(readOnly = true)
    public PaginaResponse<CuentaResponse> directorio(Usuario.Rol rol, Usuario.Estado estado, String q,
                                                     int pagina, int tamano) {
        if (rol == Usuario.Rol.ADMINISTRADOR) {
            throw ApiException.badRequest("El directorio solo incluye cuentas de alumnos y profesores");
        }
        Set<Usuario.Rol> roles = rol == null ? GESTIONABLES : EnumSet.of(rol);
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        PageRequest page = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO),
                Sort.by("rol").and(Sort.by("apellidos")).and(Sort.by("nombre")));
        return PaginaResponse.de(usuarioRepository.buscar(roles, estado, patron, page), this::resumen);
    }

    @Transactional(readOnly = true)
    public CuentaResponse detalle(Long id) {
        Usuario usuario = gestionable(id, "consultar");
        CuentaResponse r = resumen(usuario);
        r.setImpacto(impacto(usuario));
        return r;
    }

    /** CU-WEB-03 4a/4b: suspender impide iniciar sesión y cierra las sesiones abiertas. */
    @Transactional
    public CuentaResponse cambiarEstado(Long id, EstadoCuentaRequest request) {
        Usuario usuario = gestionable(id, "suspender");
        usuario.setEstado(request.getEstado());
        if (request.getEstado() == Usuario.Estado.SUSPENDIDA) {
            tokenService.revocarSesiones(usuario);
        }
        log.info("Cuenta {} → {}", usuario.getCorreo(), request.getEstado());
        return resumen(usuarioRepository.save(usuario));
    }

    /**
     * CU-WEB-03 4c/4d: elimina la cuenta. Las FK con ON DELETE CASCADE borran, dentro de la misma
     * transacción, auditoría → calificaciones → ensayos → inscripciones → tareas → grupos (RN-WEB-05).
     * Si algo falla se revierte todo (E2) y los PDF solo se borran después de confirmar.
     */
    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = gestionable(id, "eliminar");
        List<String> pdfs = new ArrayList<>(usuario.getRol() == Usuario.Rol.PROFESOR
                ? ensayoRepository.rutasDeProfesor(usuario)
                : ensayoRepository.rutasDeAlumno(usuario));
        if (usuario.tieneFoto()) {
            pdfs.add(usuario.getFotoPerfil());
        }
        usuarioRepository.delete(usuario);
        usuarioRepository.flush(); // si la cascada falla, que falle aquí y se revierta completa
        almacenamiento.eliminarAlConfirmar(pdfs);
        log.info("Cuenta eliminada: {} ({}), {} PDF(s)", usuario.getCorreo(), usuario.getRol(), pdfs.size());
    }

    /**
     * Entregas de un alumno, para que el administrador elija cuál eliminar. Sin calificación ni mensaje:
     * el administrador no ve calificaciones (RN-WEB-03), solo qué se entregó, cuándo y en qué estado.
     */
    @Transactional(readOnly = true)
    public List<EntregaResponse> entregasDeAlumno(Long idAlumno) {
        List<EntregaResponse> entregas = entregaService.historial(alumno(idAlumno));
        entregas.forEach(e -> {
            e.setCalificacionFinal(null);
            e.setModificadoPorDocente(null);
            e.setMensaje(null);
        });
        return entregas;
    }

    /**
     * Elimina una entrega con su calificación y auditoría (ON DELETE CASCADE) para que el alumno pueda
     * volver a subir el ensayo. Es la única excepción a RN-WEB-04; si la tarea ya cerró, el alumno solo
     * podrá reenviar si el profesor amplía la fecha límite (RN-WEB-02).
     */
    @Transactional
    public void eliminarEntrega(Long idAlumno, Long idEnsayo) {
        Usuario alumno = alumno(idAlumno);
        Ensayo ensayo = ensayoRepository.findById(idEnsayo)
                .filter(e -> e.getAlumno().getIdUsuario().equals(alumno.getIdUsuario()))
                .orElseThrow(() -> ApiException.notFound("La entrega no existe"));
        if (ensayo.getEstado() == Ensayo.Estado.EN_REVISION) {
            // El motor la está calificando: borrarla ahora dejaría al proceso en segundo plano sin entrega
            throw ApiException.conflict("La entrega se está calificando. Intenta de nuevo cuando termine.");
        }
        String ruta = ensayo.getRutaArchivo();
        ensayoRepository.delete(ensayo);
        ensayoRepository.flush();
        almacenamiento.eliminarAlConfirmar(List.of(ruta));
        log.info("Entrega {} de {} (tarea {}) eliminada por el administrador",
                idEnsayo, alumno.getCorreo(), ensayo.getTarea().getIdTarea());
    }

    private Usuario alumno(Long id) {
        Usuario usuario = gestionable(id, "consultar");
        if (usuario.getRol() != Usuario.Rol.ALUMNO) {
            throw ApiException.badRequest("La cuenta no es de un alumno");
        }
        return usuario;
    }

    @Transactional(readOnly = true)
    public MetricasResponse metricas() {
        return new MetricasResponse(
                usuarioRepository.countByRol(Usuario.Rol.ALUMNO),
                usuarioRepository.countByRol(Usuario.Rol.PROFESOR),
                usuarioRepository.countByRolInAndEstado(GESTIONABLES, Usuario.Estado.ACTIVA),
                usuarioRepository.countByRolInAndEstado(GESTIONABLES, Usuario.Estado.SUSPENDIDA),
                grupoRepository.countByEstado(Grupo.Estado.ACTIVO),
                ensayoRepository.countByEstadoIn(EnumSet.of(Ensayo.Estado.CALIFICADO, Ensayo.Estado.POSIBLE_PLAGIO)),
                ensayoRepository.countByEstadoIn(EnumSet.of(Ensayo.Estado.EN_REVISION)));
    }

    // ---------------------------------------------------------------- Auxiliares

    /** E1: la cuenta del administrador no se gestiona desde esta interfaz (RN-AUTH-05). */
    private Usuario gestionable(Long id, String accion) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("La cuenta no existe"));
        if (usuario.getRol() == Usuario.Rol.ADMINISTRADOR) {
            throw ApiException.forbidden("No es posible " + accion + " la cuenta del administrador desde esta interfaz");
        }
        return usuario;
    }

    private CuentaResponse.Impacto impacto(Usuario usuario) {
        if (usuario.getRol() == Usuario.Rol.PROFESOR) {
            long grupos = grupoRepository.countByProfesor(usuario);
            long tareas = tareaRepository.countByGrupoProfesor(usuario);
            long entregas = ensayoRepository.countDeProfesor(usuario);
            return CuentaResponse.Impacto.builder()
                    .grupos(grupos).tareas(tareas).entregas(entregas)
                    .advertencia("Se eliminarán la cuenta del profesor y en cascada todos sus grupos ("
                            + grupos + "), tareas (" + tareas + "), entregas y calificaciones asociadas ("
                            + entregas + "). Las cuentas de los alumnos vinculados no serán afectadas.")
                    .build();
        }
        long inscripciones = inscripcionRepository.countByAlumno(usuario);
        long entregas = ensayoRepository.countByAlumno(usuario);
        return CuentaResponse.Impacto.builder()
                .inscripciones(inscripciones).entregas(entregas)
                .advertencia(entregas > 0
                        ? "El alumno tiene " + entregas + " entrega(s) con sus calificaciones que se eliminarán. "
                        + "Los grupos a los que pertenece no serán afectados."
                        : "El alumno no tiene entregas registradas. Los grupos a los que pertenece no serán afectados.")
                .build();
    }

    private CuentaResponse resumen(Usuario u) {
        return CuentaResponse.builder()
                .id(u.getIdUsuario())
                .correo(u.getCorreo())
                .nombre(u.getNombre())
                .apellidos(u.getApellidos())
                .rol(u.getRol().name())
                .estado(u.getEstado().name())
                .fechaRegistro(u.getFechaRegistro())
                .tieneFoto(u.tieneFoto())
                .build();
    }
}
