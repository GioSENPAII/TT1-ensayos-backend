package mx.ipn.escom.tt.ensayosbackend.ia;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Microservicio de calificación (sección 4.4.6). Devuelve el objeto "result" de POST /v1/grade:
 * metadata, banderas_retroalimentacion, plagio y desglose_rubrica (CU-IA-13).
 */
public interface MotorIaClient {

    ObjectNode calificar(byte[] pdf, String nombreArchivo, Long idEnsayo, Long idTarea);

    /** Error del motor (caído, timeout o respuesta inválida). La entrega queda en estado ERROR. */
    class MotorIaException extends RuntimeException {
        public MotorIaException(String message) {
            super(message);
        }

        public MotorIaException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Falla transitoria (5xx o sin conexión): Resilience4j la reintenta (sección 4.4.6). */
    class MotorIaReintentableException extends MotorIaException {
        public MotorIaReintentableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
