package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String FIRMA = "— Sistema de Calificación de Ensayos, ESCOM IPN";

    private final JavaMailSender mailSender;

    // En desarrollo local se puede desactivar (MAIL_ENABLED=false): el código solo se escribe en el log
    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    public void enviarTokenRegistro(String destinatario, String codigo) {
        enviar(destinatario, "Tu código de verificación — ESCOM",
                "Hola,\n\nTu código de verificación es:\n\n    " + codigo + "\n\n"
                        + "Este código es válido por 15 minutos.\n\n"
                        + "Si no solicitaste este registro, ignora este mensaje.\n\n" + FIRMA,
                codigo);
    }

    public void enviarOtpAdministrador(String destinatario, String codigo) {
        enviar(destinatario, "Tu código de acceso de administrador — ESCOM",
                "Hola,\n\nTu código de acceso es:\n\n    " + codigo + "\n\n"
                        + "Este código es válido por 10 minutos y solo puede usarse una vez.\n\n"
                        + "Si no intentaste iniciar sesión, ignora este mensaje.\n\n" + FIRMA,
                codigo);
    }

    public void enviarCodigoRecuperacion(String destinatario, String codigo) {
        enviar(destinatario, "Recupera tu contraseña — ESCOM",
                "Hola,\n\nTu código para restablecer la contraseña es:\n\n    " + codigo + "\n\n"
                        + "Este código es válido por 15 minutos.\n\n"
                        + "Si no solicitaste este cambio, ignora este mensaje: tu contraseña no se modificará.\n\n"
                        + FIRMA,
                codigo);
    }

    private void enviar(String destinatario, String asunto, String cuerpo, String codigo) {
        if (!mailEnabled) {
            log.info("[MAIL DESACTIVADO] {} → {} | código: {}", asunto, destinatario, codigo);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(destinatario);
            msg.setSubject(asunto);
            msg.setText(cuerpo);
            mailSender.send(msg);
            log.debug("Correo \"{}\" enviado a {}", asunto, destinatario);
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", destinatario, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el correo. Intenta de nuevo en unos minutos.");
        }
    }
}
