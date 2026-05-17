package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.dto.*;
import mx.ipn.escom.tt.ensayosbackend.entity.Token;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.repository.TokenRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.UsuarioRepository;
import mx.ipn.escom.tt.ensayosbackend.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TokenRepository tokenRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final String ALUMNO_DOMAIN = "@alumno.ipn.mx";
    private static final String PROFESOR_DOMAIN = "@ipn.mx";

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String correo = request.getCorreo().toLowerCase().trim();
        String rol = request.getRol().toUpperCase();

        validarDominioRol(correo, rol);

        if (usuarioRepository.existsByCorreo(correo)) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }

        String tokenValue = generarToken6Digitos();

        Token token = Token.builder()
                .token(tokenValue)
                .tipo(Token.Tipo.REGISTRO)
                .fechaExpiracion(LocalDateTime.now().plusMinutes(15))
                .correoPendiente(correo)
                .rolPendiente(Usuario.Rol.valueOf(rol))
                .nombrePendiente(request.getNombre().trim())
                .apellidosPendiente(request.getApellidos().trim())
                .build();

        tokenRepository.save(token);
        log.debug("Token de registro generado para {}: {}", correo, tokenValue);

        emailService.enviarTokenRegistro(correo, tokenValue);

        return new MessageResponse("Token de verificación enviado a " + correo);

    }

    @Transactional
    public AuthResponse verifyToken(VerifyTokenRequest request) {
        Token token = tokenRepository
                .findByTokenAndTipoAndUtilizadoFalse(request.getToken(), Token.Tipo.REGISTRO)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o ya utilizado"));

        if (token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El token ha expirado");
        }

        Usuario usuario = Usuario.builder()
                .nombre(token.getNombrePendiente())
                .apellidos(token.getApellidosPendiente())
                .correo(token.getCorreoPendiente())
                .contrasena(passwordEncoder.encode(request.getPassword()))
                .rol(token.getRolPendiente())
                .estado(Usuario.Estado.ACTIVO)
                .build();

        usuarioRepository.save(usuario);

        token.setUtilizado(true);
        token.setUsuario(usuario);
        tokenRepository.save(token);

        log.debug("Usuario creado: {}", usuario.getCorreo());

        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(usuario))
                .refreshToken(jwtService.generateRefreshToken(usuario))
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String mensajeGenerico = "Correo o contraseña incorrectos";

        Usuario usuario = usuarioRepository.findByCorreo(request.getCorreo().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException(mensajeGenerico));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getContrasena())) {
            throw new IllegalArgumentException(mensajeGenerico);
        }

        if (usuario.getEstado() == Usuario.Estado.SUSPENDIDO) {
            throw new SecurityException("Cuenta suspendida. Contacta al administrador.");
        }

        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(usuario))
                .refreshToken(jwtService.generateRefreshToken(usuario))
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol().name())
                .build();
    }

    private void validarDominioRol(String correo, String rol) {
        if (rol.equals("ADMINISTRADOR")) {
            throw new IllegalArgumentException("Rol no válido");
        }
        if (rol.equals("ALUMNO") && !correo.endsWith(ALUMNO_DOMAIN)) {
            throw new IllegalArgumentException(
                    "Los alumnos deben usar correo @alumno.ipn.mx");
        }
        if (rol.equals("PROFESOR") && !correo.endsWith(PROFESOR_DOMAIN)) {
            throw new IllegalArgumentException(
                    "Los profesores deben usar correo @ipn.mx");
        }
        if (!rol.equals("ALUMNO") && !rol.equals("PROFESOR")) {
            throw new IllegalArgumentException("Rol no válido");
        }
    }

    private String generarToken6Digitos() {
        SecureRandom random = new SecureRandom();
        int numero = 100000 + random.nextInt(900000);
        return String.valueOf(numero);
    }
}
