package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.dto.CuentaResponse;
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
        List<String> pdfs = usuario.getRol() == Usuario.Rol.PROFESOR
                ? ensayoRepository.rutasDeProfesor(usuario)
                : ensayoRepository.rutasDeAlumno(usuario);
        usuarioRepository.delete(usuario);
        usuarioRepository.flush(); // si la cascada falla, que falle aquí y se revierta completa
        almacenamiento.eliminarAlConfirmar(pdfs);
        log.info("Cuenta eliminada: {} ({}), {} PDF(s)", usuario.getCorreo(), usuario.getRol(), pdfs.size());
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
                .build();
    }
}
