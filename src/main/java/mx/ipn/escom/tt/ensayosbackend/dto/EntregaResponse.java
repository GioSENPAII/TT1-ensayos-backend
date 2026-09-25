package mx.ipn.escom.tt.ensayosbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Entrega de un ensayo. En listas se omite "reporte"; en el detalle se incluye si ya está calificada. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntregaResponse {
    private Long id;
    private String nombreArchivo;
    private Integer tamanoBytes;
    private LocalDateTime fechaEntrega;
    private Long tareaId;
    private String tarea;
    private Long grupoId;
    private String grupo;
    /** EN_REVISION, CALIFICADO, POSIBLE_PLAGIO o ERROR. */
    private String estado;
    private BigDecimal calificacionFinal;
    private Boolean modificadoPorDocente;
    /** Solo profesor. */
    private AlumnoInscritoResponse alumno;
    /** Explicación cuando el estado es ERROR. */
    private String mensaje;
    private ReporteResponse reporte;
}
