package mx.ipn.escom.tt.ensayosbackend.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/** Cliente del microservicio Python real (AI_MODE=real). */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "real")
public class HttpMotorIaClient implements MotorIaClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpMotorIaClient(@Value("${app.ia.base-url}") String baseUrl,
                             @Value("${app.ia.api-key}") String apiKey,
                             @Value("${app.ia.connect-timeout}") Duration connectTimeout,
                             @Value("${app.ia.read-timeout}") Duration readTimeout,
                             ObjectMapper objectMapper) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("AI_MODE=real requiere AI_BASE_URL y AI_API_KEY");
        }
        // 5 s para conectar y 60 s de lectura (sección 4.4.6); cubre el arranque en frío de Cloud Run
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .defaultHeader("X-API-Key", apiKey)
                .build();
        this.objectMapper = objectMapper;
        log.info("Motor de IA: microservicio real en {}", baseUrl);
    }

    @Override
    public ObjectNode calificar(byte[] pdf, String nombreArchivo, Long idEnsayo, Long idTarea) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        // Se envía el nombre original: el criterio "Formato de Archivo" evalúa la nomenclatura
        form.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        });
        form.add("submission_id", String.valueOf(idEnsayo));
        form.add("task_id", String.valueOf(idTarea));

        long inicio = System.nanoTime();
        try {
            JsonNode body = restClient.post()
                    .uri("/v1/grade")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.path("result").isObject()) {
                throw new MotorIaException("Respuesta del motor de IA sin 'result'");
            }
            ObjectNode result = (ObjectNode) body.get("result");
            log.info("IA real: entrega {} calificada en {} s → {} (plagio: {})", idEnsayo,
                    String.format("%.1f", (System.nanoTime() - inicio) / 1e9),
                    result.path("metadata").path("calificacion_final").asText("?"),
                    result.path("plagio").path("detectado").asBoolean(false));
            return result;
        } catch (RestClientResponseException e) {
            String detalle = mensajeDeError(e.getResponseBodyAsString());
            log.warn("Motor de IA respondió {}: {}", e.getStatusCode(), detalle);
            throw new MotorIaException("El motor de IA respondió " + e.getStatusCode().value() + ": " + detalle, e);
        } catch (ResourceAccessException e) {
            log.warn("Motor de IA inaccesible: {}", e.getMessage());
            throw new MotorIaException("El motor de IA no respondió a tiempo", e);
        }
    }

    // ErrorResponse del microservicio: {status, error_code, message, request_id}
    private String mensajeDeError(String body) {
        try {
            JsonNode n = objectMapper.readTree(body);
            return n.path("error_code").asText("?") + " — " + n.path("message").asText(body);
        } catch (Exception ex) {
            return body;
        }
    }
}
