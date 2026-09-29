package mx.ipn.escom.tt.ensayosbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Cuenta vista por el administrador (CU-WEB-03). Nunca incluye ensayos ni calificaciones (RN-WEB-03). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CuentaResponse {
    private Long id;
    private String correo;
    private String nombre;
    private String apellidos;
    private String rol;
    private String estado;
    private LocalDateTime fechaRegistro;
    /** Solo en el detalle: lo que se borraría al eliminar la cuenta (RF-ADM-03). */
    private Impacto impacto;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Impacto {
        /** Alumno: grupos en los que está inscrito (perderá la inscripción). */
        private Long inscripciones;
        /** Profesor: grupos y tareas que creó. */
        private Long grupos;
        private Long tareas;
        /** Entregas (y sus calificaciones) que se eliminarán. */
        private long entregas;
        /** Texto de advertencia para el diálogo de confirmación (CU-WEB-03 4c/4d). */
        private String advertencia;
    }
}
