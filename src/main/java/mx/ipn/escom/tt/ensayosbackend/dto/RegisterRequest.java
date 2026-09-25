package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre es demasiado largo")
    private String nombre;
    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100, message = "Los apellidos son demasiado largos")
    private String apellidos;
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 100, message = "El correo es demasiado largo")
    private String correo;
    @NotBlank(message = "El rol es obligatorio")
    private String rol;
}
