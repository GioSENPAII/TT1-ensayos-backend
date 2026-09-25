package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.dto.*;
import mx.ipn.escom.tt.ensayosbackend.entity.Token;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.repository.TokenRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.UsuarioRepository;
import mx.ipn.escom.tt.ensayosbackend.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ALUMNO_DOMAIN = "@alumno.ipn.mx";
    private static final String PROFESOR_DOMAIN = "@ipn.mx";

    private static final int MINUTOS_VERIFICACION = 15;
    private static final int MINUTOS_OTP = 10;
    private static final int MINUTOS_RECUPERACION = 15;
    private static final int SEGUNDOS_ENTRE_REENVIOS = 60;

    private static final String MSG_CREDENCIALES = "Correo o contraseña incorrectos";
    private static final String MSG_SUSPENDIDA = "Tu cuenta ha sido suspendida. Contacta al administrador.";
    private static final String MSG_CODIGO_INVALIDO = "Código inválido o expirado. Solicita uno nuevo.";

    private final UsuarioRepository usuarioRepository;
    private final TokenRepository tokenRepository;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // ---------------------------------------------------------------- Registro (CU-AUTH-01 / 02)

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String correo = normalizar(request.getCorreo());
        Usuario.Rol rol = validarDominioRol(correo, request.getRol());

        if (usuarioRepository.existsByCorreo(correo)) {
            throw ApiException.conflict("Este correo ya tiene una cuenta registrada");
        }

        // Si ya había un registro pendiente para este correo, se reemplaza
        tokenRepository.invalidarPorCorreoPendiente(correo, Token.Tipo.VERIFICACION);
        String codigo = crearCodigoRegistro(correo, rol, request.getNombre().trim(), request.getApellidos().trim());
        emailService.enviarTokenRegistro(correo, codigo);

        return new MessageResponse("Código de verificación enviado a " + correo);
    }

    /** CU-AUTH-01/02 E3: pedir un código nuevo sin reiniciar el registro. */
    @Transactional
    public MessageResponse resendToken(CorreoRequest request) {
        String correo = normalizar(request.getCorreo());
        Token anterior = tokenRepository
                .findFirstByCorreoPendienteAndTipoOrderByFechaCreacionDesc(correo, Token.Tipo.VERIFICACION)
                .filter(t -> !usuarioRepository.existsByCorreo(correo))
                .orElseThrow(() -> ApiException.notFound(
                        "No hay un registro pendiente para este correo. Inicia el registro de nuevo."));

        if (anterior.getFechaCreacion().isAfter(LocalDateTime.now().minusSeconds(SEGUNDOS_ENTRE_REENVIOS))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Espera un minuto antes de solicitar otro código.");
        }

        tokenRepository.invalidarPorCorreoPendiente(correo, Token.Tipo.VERIFICACION);
        String codigo = crearCodigoRegistro(correo, anterior.getRolPendiente(),
                anterior.getNombrePendiente(), anterior.getApellidosPendiente());
        emailService.enviarTokenRegistro(correo, codigo);

        return new MessageResponse("Código de verificación reenviado a " + correo);
    }

    // noRollbackFor: un código incorrecto debe dejar registrado el intento fallido
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyToken(VerifyTokenRequest request) {
        String correo = normalizar(request.getCorreo());
        Token token = tokenRepository
                .findFirstByCorreoPendienteAndTipoAndUtilizadoFalseOrderByFechaCreacionDesc(
                        correo, Token.Tipo.VERIFICACION)
                .orElseThrow(() -> ApiException.badRequest(MSG_CODIGO_INVALIDO));

        tokenService.validarCodigo(token, request.getToken());

        if (usuarioRepository.existsByCorreo(correo)) {
            throw ApiException.conflict("Este correo ya tiene una cuenta registrada");
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre(token.getNombrePendiente())
                .apellidos(token.getApellidosPendiente())
                .correo(correo)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(token.getRolPendiente())
                .estado(Usuario.Estado.ACTIVA)
                .build());

        token.setUsuario(usuario);
        tokenRepository.save(token);
        log.debug("Usuario creado: {}", correo);

        return emitirSesion(usuario);
    }

    // ---------------------------------------------------------------- Inicio de sesión (CU-AUTH-04)

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByCorreo(normalizar(request.getCorreo()))
                .orElseThrow(() -> ApiException.unauthorized(MSG_CREDENCIALES));

        // El administrador no tiene contraseña: solo puede entrar por OTP
        if (usuario.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw ApiException.unauthorized(MSG_CREDENCIALES);
        }
        if (usuario.getEstado() == Usuario.Estado.SUSPENDIDA) {
            throw ApiException.forbidden(MSG_SUSPENDIDA);
        }
        return emitirSesion(usuario);
    }

    // ---------------------------------------------------------------- Administrador por OTP (CU-AUTH-03)

    @Transactional
    public MessageResponse requestAdminOtp(CorreoRequest request) {
        usuarioRepository.findByCorreo(normalizar(request.getCorreo()))
                .filter(u -> u.getRol() == Usuario.Rol.ADMINISTRADOR)
                .filter(u -> u.getEstado() == Usuario.Estado.ACTIVA)
                .ifPresent(admin -> emailService.enviarOtpAdministrador(admin.getCorreo(),
                        tokenService.crearCodigo(admin, Token.Tipo.OTP, MINUTOS_OTP)));

        // E1: misma respuesta aunque el correo no sea del administrador
        return new MessageResponse("Si el correo corresponde al administrador, recibirás un código de acceso.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyAdminOtp(VerifyOtpRequest request) {
        Usuario admin = usuarioRepository.findByCorreo(normalizar(request.getCorreo()))
                .filter(u -> u.getRol() == Usuario.Rol.ADMINISTRADOR)
                .orElseThrow(() -> ApiException.badRequest(MSG_CODIGO_INVALIDO));

        Token token = tokenRepository
                .findFirstByUsuarioAndTipoAndUtilizadoFalseOrderByFechaCreacionDesc(admin, Token.Tipo.OTP)
                .orElseThrow(() -> ApiException.badRequest(MSG_CODIGO_INVALIDO));

        tokenService.validarCodigo(token, request.getOtp());
        return emitirSesion(admin);
    }

    // ---------------------------------------------------------------- Recuperar contraseña (CU-AUTH-06)

    @Transactional
    public MessageResponse forgotPassword(CorreoRequest request) {
        usuarioRepository.findByCorreo(normalizar(request.getCorreo()))
                .filter(u -> u.getRol() != Usuario.Rol.ADMINISTRADOR)
                .filter(u -> u.getEstado() == Usuario.Estado.ACTIVA)
                .ifPresent(u -> emailService.enviarCodigoRecuperacion(u.getCorreo(),
                        tokenService.crearCodigo(u, Token.Tipo.RECUPERACION, MINUTOS_RECUPERACION)));

        // E3: no se revela si la cuenta existe
        return new MessageResponse("Si el correo está registrado, recibirás un código de recuperación.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByCorreo(normalizar(request.getCorreo()))
                .filter(u -> u.getRol() != Usuario.Rol.ADMINISTRADOR)
                .orElseThrow(() -> ApiException.badRequest(MSG_CODIGO_INVALIDO));

        Token token = tokenRepository
                .findFirstByUsuarioAndTipoAndUtilizadoFalseOrderByFechaCreacionDesc(usuario, Token.Tipo.RECUPERACION)
                .orElseThrow(() -> ApiException.badRequest(MSG_CODIGO_INVALIDO));

        tokenService.validarCodigo(token, request.getCodigo());

        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuarioRepository.save(usuario);
        tokenService.revocarSesiones(usuario);

        return new MessageResponse("Contraseña actualizada. Inicia sesión con tu nueva contraseña.");
    }

    // ---------------------------------------------------------------- Sesión (sección 4.4.4, CU-AUTH-05)

    // noRollbackFor: si se detecta reutilización, la revocación de sesiones debe persistir
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        Usuario usuario = tokenService.consumirRefreshToken(request.getRefreshToken());
        if (usuario.getEstado() == Usuario.Estado.SUSPENDIDA) {
            tokenService.revocarSesiones(usuario);
            throw ApiException.forbidden(MSG_SUSPENDIDA);
        }
        return emitirSesion(usuario);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        tokenService.revocarRefreshToken(request.getRefreshToken());
    }

    // ---------------------------------------------------------------- Auxiliares

    private AuthResponse emitirSesion(Usuario usuario) {
        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(usuario))
                .refreshToken(tokenService.emitirRefreshToken(usuario))
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol().name())
                .build();
    }

    private String crearCodigoRegistro(String correo, Usuario.Rol rol, String nombre, String apellidos) {
        String codigo = tokenService.generarCodigo();
        tokenRepository.save(Token.builder()
                .token(codigo)
                .tipo(Token.Tipo.VERIFICACION)
                .fechaExpiracion(LocalDateTime.now().plusMinutes(MINUTOS_VERIFICACION))
                .correoPendiente(correo)
                .rolPendiente(rol)
                .nombrePendiente(nombre)
                .apellidosPendiente(apellidos)
                .build());
        return codigo;
    }

    /** RN-AUTH-01: el dominio del correo debe corresponder al rol seleccionado. */
    private Usuario.Rol validarDominioRol(String correo, String rolTexto) {
        String rol = rolTexto == null ? "" : rolTexto.trim().toUpperCase();
        if (rol.equals("ALUMNO")) {
            if (!correo.endsWith(ALUMNO_DOMAIN)) {
                throw ApiException.badRequest("Los alumnos deben usar correo @alumno.ipn.mx");
            }
            return Usuario.Rol.ALUMNO;
        }
        if (rol.equals("PROFESOR")) {
            if (!correo.endsWith(PROFESOR_DOMAIN)) {
                throw ApiException.badRequest("Los profesores deben usar correo @ipn.mx");
            }
            return Usuario.Rol.PROFESOR;
        }
        throw ApiException.badRequest("Rol no válido");
    }

    private static String normalizar(String correo) {
        return correo.toLowerCase().trim();
    }
}
