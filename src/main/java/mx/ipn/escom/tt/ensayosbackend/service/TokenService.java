package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.entity.Token;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Códigos de 6 dígitos (verificación, OTP, recuperación) y refresh tokens (RNF-04, sección 4.4.4).
 * Los métodos que lanzan ApiException tras incrementar "intentos" deben llamarse desde transacciones
 * con noRollbackFor = ApiException.class para que el incremento se conserve.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final int MAX_INTENTOS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenRepository tokenRepository;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshExpirationMs;

    public String generarCodigo() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    /** Crea un código para un usuario existente (OTP o recuperación), invalidando los anteriores del mismo tipo. */
    public String crearCodigo(Usuario usuario, Token.Tipo tipo, int minutosVigencia) {
        tokenRepository.invalidarPorUsuario(usuario, tipo);
        String codigo = generarCodigo();
        tokenRepository.save(Token.builder()
                .usuario(usuario)
                .token(codigo)
                .tipo(tipo)
                .fechaExpiracion(LocalDateTime.now().plusMinutes(minutosVigencia))
                .build());
        return codigo;
    }

    /**
     * Valida un código: vigencia, uso único y máximo de intentos. Si el código es incorrecto
     * incrementa los intentos; al llegar al máximo el código queda invalidado.
     */
    public void validarCodigo(Token token, String codigoIngresado) {
        if (token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("El código ha expirado. Solicita uno nuevo.");
        }
        if (token.getIntentos() >= MAX_INTENTOS) {
            throw ApiException.badRequest("Demasiados intentos. Solicita un código nuevo.");
        }
        boolean coincide = MessageDigest.isEqual(
                token.getToken().getBytes(StandardCharsets.UTF_8),
                codigoIngresado.trim().getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            token.setIntentos(token.getIntentos() + 1);
            if (token.getIntentos() >= MAX_INTENTOS) {
                token.setUtilizado(true);
            }
            tokenRepository.save(token);
            int restantes = MAX_INTENTOS - token.getIntentos();
            throw ApiException.badRequest(restantes > 0
                    ? "Código incorrecto. Te " + (restantes == 1 ? "queda 1 intento." : "quedan " + restantes + " intentos.")
                    : "Demasiados intentos. Solicita un código nuevo.");
        }
        token.setUtilizado(true);
        tokenRepository.save(token);
    }

    /** Emite un refresh token opaco (43 caracteres) y guarda solo su hash SHA-256. */
    public String emitirRefreshToken(Usuario usuario) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(Token.builder()
                .usuario(usuario)
                .token(sha256(valor))
                .tipo(Token.Tipo.REFRESH)
                .fechaExpiracion(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000))
                .build());
        return valor;
    }

    /**
     * Consume un refresh token (rotación). Si el token ya había sido usado se asume robo:
     * se revocan todas las sesiones del usuario (sección 4.4.4).
     */
    public Usuario consumirRefreshToken(String valor) {
        Token token = tokenRepository.findByTokenAndTipo(sha256(valor), Token.Tipo.REFRESH)
                .orElseThrow(() -> ApiException.unauthorized("Sesión inválida. Inicia sesión de nuevo."));
        Usuario usuario = token.getUsuario();
        if (token.isUtilizado()) {
            log.warn("Reutilización de refresh token detectada para {}", usuario.getCorreo());
            tokenRepository.invalidarPorUsuario(usuario, Token.Tipo.REFRESH);
            throw ApiException.unauthorized("Sesión inválida. Inicia sesión de nuevo.");
        }
        if (token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw ApiException.unauthorized("La sesión expiró. Inicia sesión de nuevo.");
        }
        token.setUtilizado(true);
        tokenRepository.save(token);
        return usuario;
    }

    /** Cierre de sesión: invalida el refresh token si existe. No revela si era válido. */
    public void revocarRefreshToken(String valor) {
        tokenRepository.findByTokenAndTipo(sha256(valor), Token.Tipo.REFRESH).ifPresent(t -> {
            t.setUtilizado(true);
            tokenRepository.save(t);
        });
    }

    public void revocarSesiones(Usuario usuario) {
        tokenRepository.invalidarPorUsuario(usuario, Token.Tipo.REFRESH);
    }

    private static String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
