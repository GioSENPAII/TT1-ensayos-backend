package mx.ipn.escom.tt.ensayosbackend.dto;

/** Aviso de privacidad en Markdown, con la versión que el usuario acepta al registrarse. */
public record AvisoPrivacidadResponse(String version, String fechaActualizacion, String contenido) {
}
