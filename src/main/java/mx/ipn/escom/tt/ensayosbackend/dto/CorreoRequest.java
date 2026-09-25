package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Petición que solo lleva el correo: reenvío de código, recuperación de contraseña y OTP de admin. */
@Data
public class CorreoRequest {
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    private String correo;
}
