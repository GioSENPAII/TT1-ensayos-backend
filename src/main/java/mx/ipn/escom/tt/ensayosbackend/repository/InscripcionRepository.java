package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Inscripcion;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    @EntityGraph(attributePaths = {"grupo", "grupo.profesor"})
    List<Inscripcion> findByAlumnoOrderByFechaInscripcionDesc(Usuario alumno);

    @EntityGraph(attributePaths = {"alumno"})
    @Query("SELECT i FROM Inscripcion i WHERE i.grupo = :grupo "
            + "ORDER BY i.alumno.apellidos, i.alumno.nombre")
    List<Inscripcion> findAlumnosDelGrupo(@Param("grupo") Grupo grupo);

    Optional<Inscripcion> findByAlumnoAndGrupo(Usuario alumno, Grupo grupo);

    boolean existsByAlumnoAndGrupo(Usuario alumno, Grupo grupo);

    long countByGrupo(Grupo grupo);
}
