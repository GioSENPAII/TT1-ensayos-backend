package mx.ipn.escom.tt.ensayosbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Grupo visto por su profesor: incluye el código de acceso y contadores. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrupoResponse {
    private Long id;
    private String nombre;
    private String codigoAcceso;
    private String estado;
    private LocalDateTime fechaCreacion;
    private long totalAlumnos;
    private long totalTareas;
}
