package mx.ipn.escom.tt.ensayosbackend.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Motor simulado para desarrollo (AI_MODE=simulado, por defecto). Devuelve el mismo formato que
 * el microservicio real, con puntajes deterministas según el contenido del PDF, para no llenar
 * el historial de plagio del motor real con archivos de prueba.
 * Para probar casos especiales: un nombre de archivo con "plagio" activa la alerta y uno con
 * "error" simula una falla del motor.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "simulado", matchIfMissing = true)
public class MotorIaSimulado implements MotorIaClient {

    // Criterios y pesos de la Tabla 15 de TT2 (suman 10.0)
    private static final String[] CRITERIOS = {
            "Formato de Archivo", "Estructura Documental", "Ortografía y Sintaxis", "Título del Ensayo",
            "Introducción", "Desarrollo", "Conclusiones", "Referencias"};
    private static final double[] MAXIMOS = {0.5, 0.5, 1.0, 1.0, 1.0, 4.0, 1.5, 0.5};
    private static final String NOMENCLATURA = "^[^_]+_[^_]+_[^_]+_[^_]+\\.pdf$";

    private final ObjectMapper objectMapper;

    public MotorIaSimulado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.info("Motor de IA: SIMULADO (AI_MODE=real para usar el microservicio)");
    }

    @Override
    public ObjectNode calificar(byte[] pdf, String nombreArchivo, Long idEnsayo, Long idTarea) {
        String nombre = nombreArchivo.toLowerCase(Locale.ROOT);
        pausa();
        if (nombre.contains("error")) {
            throw new MotorIaException("Falla simulada del motor de IA");
        }

        byte[] hash = sha256(pdf);
        boolean plagio = nombre.contains("plagio");
        ArrayNode desglose = objectMapper.createArrayNode();
        double total = 0;
        for (int i = 0; i < CRITERIOS.length; i++) {
            double puntaje;
            String detalles;
            if (i == 0) {
                boolean nomenclatura = nombreArchivo.matches(NOMENCLATURA);
                puntaje = nomenclatura ? MAXIMOS[i] : MAXIMOS[i] / 2;
                detalles = nomenclatura ? "PDF con nomenclatura correcta."
                        : "Es PDF, pero no respeta Grupo_Nombre1_Nombre2_Tarea.pdf.";
            } else {
                // nivel completo, medio o nulo según un byte del hash (distribución 60/30/10)
                int v = Byte.toUnsignedInt(hash[i]) % 10;
                double factor = v < 6 ? 1.0 : v < 9 ? 0.5 : 0.0;
                puntaje = MAXIMOS[i] * factor;
                detalles = "Evaluación simulada (nivel " + (factor == 1 ? "completo" : factor > 0 ? "medio" : "nulo") + ").";
            }
            total += puntaje;
            desglose.addObject()
                    .put("criterio", CRITERIOS[i])
                    .put("puntaje_obtenido", puntaje)
                    .put("puntaje_maximo", MAXIMOS[i])
                    .put("detalles", detalles);
        }
        double finalRedondeada = BigDecimal.valueOf(total).setScale(1, RoundingMode.HALF_UP).doubleValue();

        ObjectNode result = objectMapper.createObjectNode();
        result.putObject("metadata")
                .put("archivo", nombreArchivo)
                .put("timestamp", LocalDateTime.now().toString())
                .put("calificacion_final", finalRedondeada)
                .put("observacion", plagio
                        ? "Ensayo con alerta de similitud histórica; requiere revisión docente."
                        : "Evaluación generada por el motor simulado de desarrollo.")
                .put("submission_id", String.valueOf(idEnsayo))
                .put("task_id", String.valueOf(idTarea));
        result.putObject("banderas_retroalimentacion")
                .put("plagio_detectado", plagio)
                .put("nivel_alerta_plagio", plagio ? "plagio_probable" : "sin_alerta")
                .put("requiere_revision_docente", plagio)
                .put("falta_contexto_intro", desglose.get(4).path("puntaje_obtenido").asDouble() == 0)
                .put("abuso_vinetas", desglose.get(2).path("puntaje_obtenido").asDouble() == 0);
        ObjectNode plagioNode = result.putObject("plagio").put("detectado", plagio);
        ArrayNode coincidencias = plagioNode.putArray("coincidencias");
        if (plagio) {
            coincidencias.addObject().put("document_hash", HexFormat.of().formatHex(hash)).put("similarity", 0.985);
        }
        result.set("desglose_rubrica", desglose);
        log.info("IA SIMULADA: entrega {} calificada → {}", idEnsayo, finalRedondeada);
        return result;
    }

    private static void pausa() {
        try {
            Thread.sleep(1500); // simula el tiempo de procesamiento
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
