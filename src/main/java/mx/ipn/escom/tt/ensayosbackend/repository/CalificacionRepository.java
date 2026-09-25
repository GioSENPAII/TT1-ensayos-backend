package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Calificacion;
import mx.ipn.escom.tt.ensayosbackend.entity.Ensayo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {
    Optional<Calificacion> findByEnsayo(Ensayo ensayo);
    List<Calificacion> findByEnsayoIn(Collection<Ensayo> ensayos);
}
