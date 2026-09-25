package mx.ipn.escom.tt.ensayosbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TareaResponse {
    private Long id;
    private Long groupId;
    private String grupo;
    private String nombre;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    /** PROXIMA, ABIERTA o CERRADA respecto a la hora del servidor. */
    private String disponibilidad;
    /** Solo profesor: si ya hay entregas (limita la edición, CU-WEB-05 A2). */
    private Boolean tieneEntregas;
    /** Solo alumno: su entrega en esta tarea, o null si aún no entrega ("Sin entregar"). */
    private EntregaResumen entrega;
    /** Solo alumno: true si la tarea sigue abierta y aún no ha entregado. */
    private Boolean pendiente;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntregaResumen {
        private Long id;
        private String estado;
        private LocalDateTime fechaEntrega;
    }
}
