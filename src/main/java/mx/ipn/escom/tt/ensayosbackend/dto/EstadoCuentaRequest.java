package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;

@Data
public class EstadoCuentaRequest {
    @NotNull(message = "El estado es obligatorio (ACTIVA o SUSPENDIDA)")
    private Usuario.Estado estado;
}
