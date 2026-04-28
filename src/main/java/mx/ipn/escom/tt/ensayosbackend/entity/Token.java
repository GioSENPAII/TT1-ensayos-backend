package mx.ipn.escom.tt.ensayosbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    private Long idToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(nullable = false, length = 10)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    @Builder.Default
    private boolean utilizado = false;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "correo_pendiente", length = 100)
    private String correoPendiente;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_pendiente")
    private Usuario.Rol rolPendiente;

    @Column(name = "nombre_pendiente", length = 100)
    private String nombrePendiente;

    @Column(name = "apellidos_pendiente", length = 100)
    private String apellidosPendiente;

    public enum Tipo { REGISTRO, RESET_PASSWORD }
}
