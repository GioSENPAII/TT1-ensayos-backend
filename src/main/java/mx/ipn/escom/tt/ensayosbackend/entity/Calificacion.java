package mx.ipn.escom.tt.ensayosbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "calificaciones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calificacion")
    private Long idCalificacion;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ensayo", nullable = false, unique = true)
    private Ensayo ensayo;

    @Column(name = "calificacion_final", nullable = false, precision = 4, scale = 2)
    private BigDecimal calificacionFinal;

    @Column(name = "similitud_plagio", precision = 5, scale = 4)
    private BigDecimal similitudPlagio;

    /** Resultado del motor de IA tal como lo devolvió (sección 4.7.4), con los ajustes del docente. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reporte_json", nullable = false)
    private String reporteJson;

    @Column(name = "modificado_por_docente", nullable = false)
    @Builder.Default
    private boolean modificadoPorDocente = false;

    @Column(name = "fecha_evaluacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaEvaluacion = LocalDateTime.now();

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
