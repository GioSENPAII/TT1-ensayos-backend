package mx.ipn.escom.tt.ensayosbackend.repository;

import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);

    // Directorio del administrador (CU-WEB-03): filtros opcionales por rol, estado y texto
    @Query("SELECT u FROM Usuario u WHERE u.rol IN :roles "
            + "AND (:estado IS NULL OR u.estado = :estado) "
            + "AND (:q IS NULL OR LOWER(u.correo) LIKE :q OR LOWER(CONCAT(u.nombre, ' ', u.apellidos)) LIKE :q)")
    Page<Usuario> buscar(@Param("roles") Collection<Usuario.Rol> roles,
                         @Param("estado") Usuario.Estado estado,
                         @Param("q") String q,
                         Pageable pageable);

    long countByRol(Usuario.Rol rol);

    long countByRolInAndEstado(Collection<Usuario.Rol> roles, Usuario.Estado estado);
}
