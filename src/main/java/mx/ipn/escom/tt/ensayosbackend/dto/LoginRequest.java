package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String correo;
    @NotBlank
    private String password;
}
