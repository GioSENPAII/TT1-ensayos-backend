package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UnirseGrupoRequest {
    @NotBlank(message = "El código de acceso es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9]{6}", message = "El código de acceso debe tener 6 caracteres alfanuméricos")
    private String codigo;
}
