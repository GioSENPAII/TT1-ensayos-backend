package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyTokenRequest {
    @NotBlank
    @Size(min = 6, max = 6)
    private String token;
    @NotBlank
    @Size(min = 8)
    private String password;
}
