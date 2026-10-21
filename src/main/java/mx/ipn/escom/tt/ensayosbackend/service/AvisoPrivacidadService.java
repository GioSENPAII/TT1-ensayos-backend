package mx.ipn.escom.tt.ensayosbackend.service;

import mx.ipn.escom.tt.ensayosbackend.dto.AvisoPrivacidadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Aviso de privacidad integral. El texto vive en resources/legal/aviso_privacidad.md; al cambiarlo hay que
 * subir VERSION para que quede registrado qué versión aceptó cada usuario.
 */
@Service
public class AvisoPrivacidadService {

    public static final String VERSION = "1.0";
    public static final String FECHA_ACTUALIZACION = "2026-10-21";

    private final AvisoPrivacidadResponse aviso;

    public AvisoPrivacidadService(@Value("${app.privacidad.correo-contacto}") String correoContacto) {
        try {
            String texto = new ClassPathResource("legal/aviso_privacidad.md")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{CORREO_CONTACTO}}", correoContacto);
            aviso = new AvisoPrivacidadResponse(VERSION, FECHA_ACTUALIZACION, texto);
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró el aviso de privacidad", e);
        }
    }

    public AvisoPrivacidadResponse obtener() {
        return aviso;
    }
}
