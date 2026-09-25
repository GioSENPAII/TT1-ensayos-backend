package mx.ipn.escom.tt.ensayosbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Reporte de calificación por rúbrica (CU-ALU-03, CU-WEB-02), derivado de reporte_json. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReporteResponse {
    private BigDecimal calificacionFinal;
    private BigDecimal calificacionMaxima;
    private String observacion;
    private LocalDateTime fechaEvaluacion;
    private boolean modificadoPorDocente;
    private LocalDateTime fechaModificacion;
    private boolean posiblePlagio;
    private Banderas banderas;
    private List<Criterio> criterios;
    /** Solo profesor: similitud máxima y coincidencias del historial. */
    private BigDecimal similitudMaxima;
    private List<Coincidencia> coincidencias;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Banderas {
        private boolean requiereRevisionDocente;
        private boolean faltaContextoIntro;
        private boolean abusoVinetas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Criterio {
        private String criterio;
        private BigDecimal puntajeObtenido;
        private BigDecimal puntajeMaximo;
        /** ALTO (100 %), MEDIO (parcial) o BAJO (0 %). */
        private String nivel;
        /** Descripción de la Tabla 14 que corresponde al nivel obtenido. */
        private String detalles;
        /** Detalle técnico devuelto por el motor de IA. */
        private String detallesMotor;
        private boolean modificadoPorDocente;
        /** Puntaje original de la IA cuando el docente lo ajustó. */
        private BigDecimal puntajeIa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coincidencia {
        private String documentHash;
        private BigDecimal similitud;
    }
}
