package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    private String correo;
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String codigo;
    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MENSAJE)
    private String password;
}
