package mx.ipn.escom.tt.ensayosbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Grupo visto por un alumno inscrito ("Mis Grupos", CU-ALU-01). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrupoAlumnoResponse {
    private Long id;
    private String nombre;
    private String profesor;
    private String estado;
    private LocalDateTime fechaInscripcion;
}
