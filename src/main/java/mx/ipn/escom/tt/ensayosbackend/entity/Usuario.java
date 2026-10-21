package mx.ipn.escom.tt.ensayosbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(nullable = false, unique = true, length = 100)
    private String correo;

    // NULL solo para el administrador, que se autentica por OTP (RN-AUTH-05)
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Estado estado = Estado.ACTIVA;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    // Versión del aviso de privacidad aceptada y cuándo; NULL para el administrador (se crea en la BD)
    @Column(name = "aviso_privacidad_version", length = 20)
    private String avisoPrivacidadVersion;

    @Column(name = "aviso_privacidad_fecha")
    private LocalDateTime avisoPrivacidadFecha;

    // Ruta de la foto de perfil en el almacenamiento (JPEG); NULL si no tiene
    @Column(name = "foto_perfil", length = 500)
    private String fotoPerfil;

    public boolean tieneFoto() {
        return fotoPerfil != null;
    }

    public enum Rol { ALUMNO, PROFESOR, ADMINISTRADOR }
    public enum Estado { ACTIVA, SUSPENDIDA }
}
