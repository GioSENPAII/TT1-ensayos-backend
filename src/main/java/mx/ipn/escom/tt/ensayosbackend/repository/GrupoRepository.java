package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrupoRepository extends JpaRepository<Grupo, Long> {
    List<Grupo> findByProfesorOrderByFechaCreacionDesc(Usuario profesor);
    Optional<Grupo> findByCodigoAcceso(String codigoAcceso);
    boolean existsByCodigoAcceso(String codigoAcceso);
    boolean existsByProfesorAndNombreIgnoreCase(Usuario profesor, String nombre);

    long countByEstado(Grupo.Estado estado);

    long countByProfesor(Usuario profesor);
}
