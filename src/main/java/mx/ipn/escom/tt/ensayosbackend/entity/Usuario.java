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

    public enum Rol { ALUMNO, PROFESOR, ADMINISTRADOR }
    public enum Estado { ACTIVA, SUSPENDIDA }
}
