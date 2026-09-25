package mx.ipn.escom.tt.ensayosbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tareas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarea")
    private Long idTarea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo", nullable = false)
    private Grupo grupo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDateTime fechaCierre;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    /** Disponibilidad calculada respecto a la hora actual (CU-WEB-05 pasos 8 y 9). */
    public Disponibilidad disponibilidad(LocalDateTime ahora) {
        if (ahora.isBefore(fechaApertura)) {
            return Disponibilidad.PROXIMA;
        }
        return ahora.isBefore(fechaCierre) ? Disponibilidad.ABIERTA : Disponibilidad.CERRADA;
    }

    public enum Disponibilidad { PROXIMA, ABIERTA, CERRADA }
}
