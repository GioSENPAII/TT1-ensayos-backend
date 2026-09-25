package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Usuario de la petición actual (el principal del JWT es el correo).
 * Se consulta la BD para que una suspensión surta efecto aunque el access token siga vigente.
 */
@Service
@RequiredArgsConstructor
public class UsuarioActualService {

    private final UsuarioRepository usuarioRepository;

    public Usuario obtener() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof String correo)) {
            throw ApiException.unauthorized("Sesión no válida o expirada. Inicia sesión de nuevo.");
        }
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> ApiException.unauthorized("Sesión no válida o expirada. Inicia sesión de nuevo."));
        if (usuario.getEstado() == Usuario.Estado.SUSPENDIDA) {
            throw ApiException.forbidden("Tu cuenta ha sido suspendida. Contacta al administrador.");
        }
        return usuario;
    }
}
