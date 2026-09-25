package mx.ipn.escom.tt.ensayosbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Registro de cada ajuste manual del profesor (CU-WEB-02, RF-PRO-07). */
@Entity
@Table(name = "auditoria_modificaciones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaModificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_calificacion", nullable = false)
    private Calificacion calificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profesor", nullable = false)
    private Usuario profesor;

    @Column(name = "criterio_modificado", nullable = false, length = 100)
    private String criterioModificado;

    @Column(name = "valor_original", nullable = false, precision = 4, scale = 2)
    private BigDecimal valorOriginal;

    @Column(name = "valor_ajustado", nullable = false, precision = 4, scale = 2)
    private BigDecimal valorAjustado;

    @Column(name = "fecha_modificacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaModificacion = LocalDateTime.now();
}
