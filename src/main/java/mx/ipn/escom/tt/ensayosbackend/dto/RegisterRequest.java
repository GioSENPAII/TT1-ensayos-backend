package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String nombre;
    @NotBlank
    private String apellidos;
    @NotBlank
    @Email
    private String correo;
    @NotBlank
    private String rol;
}
