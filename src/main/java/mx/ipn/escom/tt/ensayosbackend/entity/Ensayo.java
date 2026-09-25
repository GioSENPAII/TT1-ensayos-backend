package mx.ipn.escom.tt.ensayosbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ensayos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ensayo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ensayo")
    private Long idEnsayo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Usuario alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tarea", nullable = false)
    private Tarea tarea;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", nullable = false, length = 500)
    private String rutaArchivo;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "texto_extraido", columnDefinition = "LONGTEXT")
    private String textoExtraido;

    @Column(name = "tamano_bytes", nullable = false)
    private Integer tamanoBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Estado estado = Estado.EN_REVISION;

    @Column(name = "fecha_entrega", nullable = false)
    @Builder.Default
    private LocalDateTime fechaEntrega = LocalDateTime.now();

    public enum Estado { EN_REVISION, CALIFICADO, POSIBLE_PLAGIO, ERROR }
}
