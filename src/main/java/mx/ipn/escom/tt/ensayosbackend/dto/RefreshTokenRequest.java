package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "El refresh token es obligatorio")
    private String refreshToken;
}
