package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.config.AsyncConfig;
import mx.ipn.escom.tt.ensayosbackend.entity.Ensayo;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String FIRMA = "— Sistema de Calificación de Ensayos, ESCOM IPN";
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy 'a las' HH:mm", Locale.forLanguageTag("es-MX"));

    private final JavaMailSender mailSender;

    // En desarrollo local se puede desactivar (MAIL_ENABLED=false): el código solo se escribe en el log
    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String cuentaRemitente;

    // Nombre que ve el destinatario en lugar de la dirección de Gmail
    @Value("${app.mail.remitente-nombre:Sistema de Calificación de Ensayos ESCOM}")
    private String nombreRemitente;

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

    /**
     * Avisa a otro administrador que alguien pidió un código de acceso de administrador. Va en segundo plano y
     * sin propagar errores: un aviso que falla no debe impedir que quien inicia sesión reciba su código.
     */
    @Async(AsyncConfig.EJECUTOR_CORREO)
    public void avisarAccesoAdministrador(String destinatario, String correoSolicitante, LocalDateTime fecha) {
        try {
            enviar(destinatario, "Aviso: intento de acceso de administrador — ESCOM",
                    "Hola,\n\nLa cuenta de administrador " + correoSolicitante + " solicitó un código de acceso el "
                            + FECHA_HORA.format(fecha) + " (hora del centro de México).\n\n"
                            + "El código se envió solo a esa cuenta. Si no reconoces este intento, "
                            + "comunícate con esa persona.\n\n" + FIRMA,
                    null);
        } catch (ApiException e) {
            log.warn("No se pudo enviar el aviso de acceso a {}", destinatario);
        }
    }

    /** Datos de una entrega eliminada por un administrador, copiados antes de borrarla. */
    public record EntregaEliminada(String profesorCorreo, String profesorNombre, String administradorCorreo,
                                   LocalDateTime fechaEliminacion, String alumnoNombre, String alumnoCorreo,
                                   String grupoNombre, String grupoCodigo, String tareaNombre,
                                   LocalDateTime tareaCierre, String archivo, LocalDateTime fechaEntrega,
                                   Ensayo.Estado estado, BigDecimal calificacionFinal, boolean ajustadaPorDocente) {}

    /**
     * Avisa al profesor del grupo que un administrador eliminó una entrega. Incluye la calificación porque el
     * profesor ya podía verla y con el borrado deja de existir; el administrador nunca la ve (RN-WEB-03).
     */
    @Async(AsyncConfig.EJECUTOR_CORREO)
    public void avisarEntregaEliminada(EntregaEliminada e) {
        String calificacion = e.calificacionFinal() == null ? "sin calificación"
                : e.calificacionFinal().setScale(1, RoundingMode.HALF_UP).toPlainString()   // como en la app
                        + (e.ajustadaPorDocente() ? " (ajustada por ti)" : "");
        boolean cerrada = e.tareaCierre().isBefore(LocalDateTime.now());
        try {
            enviar(e.profesorCorreo(), "Entrega eliminada por un administrador — " + e.tareaNombre(),
                    "Hola, " + e.profesorNombre() + ":\n\n"
                            + "El administrador " + e.administradorCorreo() + " eliminó una entrega de tu grupo el "
                            + FECHA_HORA.format(e.fechaEliminacion()) + ".\n\n"
                            + "Alumno: " + e.alumnoNombre() + " (" + e.alumnoCorreo() + ")\n"
                            + "Grupo: " + e.grupoNombre() + " (código " + e.grupoCodigo() + ")\n"
                            + "Tarea: " + e.tareaNombre() + " — " + (cerrada ? "cerró" : "cierra") + " el "
                            + FECHA_HORA.format(e.tareaCierre()) + "\n"
                            + "Archivo: " + e.archivo() + ", entregado el " + FECHA_HORA.format(e.fechaEntrega()) + "\n"
                            + "Estado: " + estado(e.estado()) + "\n"
                            + "Calificación: " + calificacion + "\n\n"
                            + "Se eliminaron el archivo, la calificación y el historial de ajustes de esta entrega. "
                            + (cerrada
                                ? "La tarea ya cerró: el alumno solo podrá volver a enviar su ensayo si amplías la fecha de cierre."
                                : "El alumno puede volver a enviar su ensayo mientras la tarea siga abierta.")
                            + "\n\n" + FIRMA,
                    null);
        } catch (ApiException ex) {
            log.warn("No se pudo enviar el aviso de entrega eliminada a {}", e.profesorCorreo());
        }
    }

    private static String estado(Ensayo.Estado estado) {
        return switch (estado) {
            case EN_REVISION -> "En revisión";
            case CALIFICADO -> "Calificado";
            case POSIBLE_PLAGIO -> "Posible plagio";
            case ERROR -> "Error al calificar";
        };
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
            if (codigo == null) {
                // Avisos sin código: se escribe el texto completo para revisarlo en local
                log.info("[MAIL DESACTIVADO] {} → {}\n{}", asunto, destinatario, cuerpo);
            } else {
                log.info("[MAIL DESACTIVADO] {} → {} | código: {}", asunto, destinatario, codigo);
            }
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(cuentaRemitente, nombreRemitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo);
            mailSender.send(msg);
            log.debug("Correo \"{}\" enviado a {}", asunto, destinatario);
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", destinatario, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el correo. Intenta de nuevo en unos minutos.");
        }
    }
}
