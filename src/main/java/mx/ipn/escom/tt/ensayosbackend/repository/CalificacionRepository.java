package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Calificacion;
import mx.ipn.escom.tt.ensayosbackend.entity.Ensayo;
import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {
    Optional<Calificacion> findByEnsayo(Ensayo ensayo);
    List<Calificacion> findByEnsayoIn(Collection<Ensayo> ensayos);

    // Rendimiento del grupo: todas las calificaciones con su entrega, tarea y alumno
    @Query("SELECT c FROM Calificacion c JOIN FETCH c.ensayo e JOIN FETCH e.tarea t JOIN FETCH e.alumno "
            + "WHERE t.grupo = :grupo")
    List<Calificacion> findDelGrupo(@Param("grupo") Grupo grupo);
}
