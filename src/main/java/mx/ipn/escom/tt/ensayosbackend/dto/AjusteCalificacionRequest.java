package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Ajuste manual de un criterio por el profesor (CU-WEB-02 pasos 5-8). */
@Data
public class AjusteCalificacionRequest {
    @NotBlank(message = "El criterio es obligatorio")
    private String criterio;
    @NotNull(message = "El puntaje es obligatorio")
    private BigDecimal puntaje;
}
