package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void enviarTokenRegistro(String destinatario, String token) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(destinatario);
            msg.setSubject("Tu código de verificación — ESCOM TT1");
            msg.setText(
                "Hola,\n\n" +
                "Tu código de verificación es:\n\n" +
                "    " + token + "\n\n" +
                "Este código es válido por 15 minutos.\n\n" +
                "Si no solicitaste este registro, ignora este mensaje.\n\n" +
                "— Sistema de Calificación de Ensayos, ESCOM IPN"
            );
            mailSender.send(msg);
            log.debug("Token enviado a {}", destinatario);
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", destinatario, e.getMessage());
            throw new RuntimeException("No se pudo enviar el correo de verificación");
        }
    }
}
