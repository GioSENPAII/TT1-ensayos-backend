package mx.ipn.escom.tt.ensayosbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Alumno en la lista de un grupo (CU-WEB-04). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlumnoInscritoResponse {
    private Long id;
    private String nombre;
    private String apellidos;
    private String correo;
    private LocalDateTime fechaInscripcion;
}
