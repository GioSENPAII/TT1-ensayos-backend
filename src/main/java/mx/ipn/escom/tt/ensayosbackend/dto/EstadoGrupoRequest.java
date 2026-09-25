package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import mx.ipn.escom.tt.ensayosbackend.entity.Grupo;

@Data
public class EstadoGrupoRequest {
    @NotNull(message = "El estado es obligatorio (ACTIVO o INACTIVO)")
    private Grupo.Estado estado;
}
