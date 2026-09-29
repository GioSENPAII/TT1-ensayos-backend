package mx.ipn.escom.tt.ensayosbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.config.AsyncConfig;
import mx.ipn.escom.tt.ensayosbackend.entity.Calificacion;
import mx.ipn.escom.tt.ensayosbackend.entity.Ensayo;
import mx.ipn.escom.tt.ensayosbackend.ia.MotorIaClient;
import mx.ipn.escom.tt.ensayosbackend.repository.CalificacionRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.EnsayoRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Califica una entrega en segundo plano (RNF-09, corrección C6): lee el PDF guardado, llama al motor
 * de IA fuera de cualquier transacción y deja la entrega CALIFICADO, POSIBLE_PLAGIO o ERROR.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalificacionWorker {

    private final EnsayoRepository ensayoRepository;
    private final CalificacionRepository calificacionRepository;
    private final AlmacenamientoService almacenamiento;
    private final MotorIaClient motorIa;
    private final PoliticaPlagio politicaPlagio;
    private final TransactionTemplate tx;

    private record Pendiente(byte[] pdf, String nombreArchivo, Long idTarea) {
    }

    @Async(AsyncConfig.EJECUTOR_CALIFICACION)
    public void calificar(Long idEnsayo) {
        Pendiente pendiente = tx.execute(status -> ensayoRepository.findById(idEnsayo)
                .filter(e -> e.getEstado() == Ensayo.Estado.EN_REVISION)
                .map(e -> new Pendiente(almacenamiento.leer(e.getRutaArchivo()), e.getNombreArchivo(),
                        e.getTarea().getIdTarea()))
                .orElse(null));
        if (pendiente == null) {
            return; // se eliminó la entrega o ya se calificó
        }

        try {
            ObjectNode resultado = motorIa.calificar(pendiente.pdf(), pendiente.nombreArchivo(), idEnsayo,
                    pendiente.idTarea());
            tx.executeWithoutResult(status -> guardarCalificacion(idEnsayo, resultado));
        } catch (CallNotPermittedException e) {
            log.warn("Entrega {} sin calificar: circuito del motor de IA abierto", idEnsayo);
            marcarError(idEnsayo);
        } catch (MotorIaClient.MotorIaException | IllegalArgumentException e) {
            log.warn("Entrega {} sin calificar: {}", idEnsayo, e.getMessage());
            marcarError(idEnsayo);
        } catch (RuntimeException e) {
            log.error("Error inesperado al calificar la entrega {}", idEnsayo, e);
            marcarError(idEnsayo);
        }
    }

    public void marcarError(Long idEnsayo) {
        tx.executeWithoutResult(status -> ensayoRepository.findById(idEnsayo)
                .filter(e -> e.getEstado() == Ensayo.Estado.EN_REVISION)
                .ifPresent(e -> e.setEstado(Ensayo.Estado.ERROR)));
    }

    private void guardarCalificacion(Long idEnsayo, ObjectNode resultado) {
        Ensayo ensayo = ensayoRepository.findById(idEnsayo).orElse(null);
        if (ensayo == null) {
            return; // la tarea o el grupo se eliminaron mientras se calificaba
        }
        JsonNode metadata = resultado.path("metadata");
        JsonNode desglose = resultado.path("desglose_rubrica");
        if (!metadata.path("calificacion_final").isNumber() || !desglose.isArray() || desglose.isEmpty()) {
            throw new IllegalArgumentException("Respuesta del motor de IA incompleta");
        }

        boolean plagio = politicaPlagio.esPlagio(resultado);
        BigDecimal similitud = null;
        for (JsonNode c : resultado.path("plagio").path("coincidencias")) {
            BigDecimal s = c.path("similarity").decimalValue();
            if (similitud == null || s.compareTo(similitud) > 0) {
                similitud = s;
            }
        }

        Calificacion calificacion = calificacionRepository.findByEnsayo(ensayo)
                .orElseGet(() -> Calificacion.builder().ensayo(ensayo).build());
        // RN-IA-04: 0.0-10.0 redondeada a un decimal
        BigDecimal finalNota = metadata.path("calificacion_final").decimalValue()
                .max(BigDecimal.ZERO).min(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP);
        calificacion.setCalificacionFinal(finalNota);
        calificacion.setSimilitudPlagio(similitud == null ? null : similitud.setScale(4, RoundingMode.HALF_UP));
        calificacion.setReporteJson(resultado.toString());
        calificacion.setModificadoPorDocente(false);
        calificacion.setFechaEvaluacion(LocalDateTime.now());
        calificacion.setFechaModificacion(null);
        calificacionRepository.save(calificacion);

        // RN-IA-03: la alerta de plagio no anula la calificación, solo cambia el estado
        ensayo.setEstado(plagio ? Ensayo.Estado.POSIBLE_PLAGIO : Ensayo.Estado.CALIFICADO);
    }
}
