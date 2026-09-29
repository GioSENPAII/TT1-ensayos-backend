package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TareaRepository extends JpaRepository<Tarea, Long> {
    List<Tarea> findByGrupoOrderByFechaCierreAsc(Grupo grupo);

    // Vista del alumno: solo tareas cuya fecha de apertura ya llegó (CU-WEB-05 paso 8)
    List<Tarea> findByGrupoAndFechaAperturaLessThanEqualOrderByFechaCierreAsc(Grupo grupo, LocalDateTime ahora);

    long countByGrupo(Grupo grupo);

    long countByGrupoProfesor(mx.ipn.escom.tt.ensayosbackend.entity.Usuario profesor);

    boolean existsByGrupoAndNombreIgnoreCase(Grupo grupo, String nombre);

    boolean existsByGrupoAndNombreIgnoreCaseAndIdTareaNot(Grupo grupo, String nombre, Long idTarea);
}
