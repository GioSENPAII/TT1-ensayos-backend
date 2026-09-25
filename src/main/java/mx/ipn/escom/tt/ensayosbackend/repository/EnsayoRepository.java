package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Ensayo;
import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Tarea;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EnsayoRepository extends JpaRepository<Ensayo, Long> {

    List<Ensayo> findByAlumnoAndTareaIn(Usuario alumno, Collection<Tarea> tareas);

    boolean existsByTarea(Tarea tarea);

    java.util.Optional<Ensayo> findByAlumnoAndTarea(Usuario alumno, Tarea tarea);

    // Historial del alumno (CU-ALU-04), de la más reciente a la más antigua
    @EntityGraph(attributePaths = {"tarea", "tarea.grupo"})
    List<Ensayo> findByAlumnoOrderByFechaEntregaDesc(Usuario alumno);

    // Entregas de una tarea para el profesor (CU-WEB-02)
    @EntityGraph(attributePaths = {"alumno"})
    List<Ensayo> findByTareaOrderByFechaEntregaDesc(Tarea tarea);

    // RF-PRO-04: al remover a un alumno de un grupo se borran sus entregas en ese grupo
    // (las calificaciones y auditorías caen por ON DELETE CASCADE)
    @Modifying
    @Query("DELETE FROM Ensayo e WHERE e.alumno = :alumno "
            + "AND e.tarea IN (SELECT t FROM Tarea t WHERE t.grupo = :grupo)")
    void deleteByAlumnoAndGrupo(@Param("alumno") Usuario alumno, @Param("grupo") Grupo grupo);
}
