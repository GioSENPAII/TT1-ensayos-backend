package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Token;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {

    // Registro pendiente: el usuario aún no existe, se busca por el correo capturado
    Optional<Token> findFirstByCorreoPendienteAndTipoAndUtilizadoFalseOrderByFechaCreacionDesc(
            String correoPendiente, Token.Tipo tipo);

    // Incluye los ya invalidados: sirve para reenviar el código con los mismos datos
    Optional<Token> findFirstByCorreoPendienteAndTipoOrderByFechaCreacionDesc(
            String correoPendiente, Token.Tipo tipo);

    Optional<Token> findFirstByUsuarioAndTipoAndUtilizadoFalseOrderByFechaCreacionDesc(
            Usuario usuario, Token.Tipo tipo);

    // Refresh tokens: el valor guardado es el hash SHA-256
    Optional<Token> findByTokenAndTipo(String token, Token.Tipo tipo);

    @Modifying
    @Query("UPDATE Token t SET t.utilizado = true "
            + "WHERE t.correoPendiente = :correo AND t.tipo = :tipo AND t.utilizado = false")
    void invalidarPorCorreoPendiente(@Param("correo") String correo, @Param("tipo") Token.Tipo tipo);

    @Modifying
    @Query("UPDATE Token t SET t.utilizado = true "
            + "WHERE t.usuario = :usuario AND t.tipo = :tipo AND t.utilizado = false")
    void invalidarPorUsuario(@Param("usuario") Usuario usuario, @Param("tipo") Token.Tipo tipo);
}
