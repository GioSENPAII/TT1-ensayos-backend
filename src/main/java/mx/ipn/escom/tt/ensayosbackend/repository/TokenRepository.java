package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {
    Optional<Token> findByTokenAndTipoAndUtilizadoFalse(String token, Token.Tipo tipo);
}
