package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CrearGrupoRequest {
    @NotBlank(message = "El nombre del grupo es obligatorio")
    @Size(max = 100, message = "El nombre del grupo es demasiado largo")
    private String nombre;
}
