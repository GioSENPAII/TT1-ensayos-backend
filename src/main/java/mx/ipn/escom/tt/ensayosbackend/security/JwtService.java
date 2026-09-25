package mx.ipn.escom.tt.ensayosbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") long accessExpiration) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.accessExpiration = accessExpiration;
    }

    // El refresh token es opaco y se gestiona en TokenService (sección 4.4.4)
    public String generateAccessToken(Usuario usuario) {
        return Jwts.builder()
                .subject(usuario.getCorreo())
                .claims(Map.of(
                        "rol", "ROLE_" + usuario.getRol().name(),
                        "correo", usuario.getCorreo(),
                        "nombre", usuario.getNombre()
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractCorreo(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return extractAllClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
