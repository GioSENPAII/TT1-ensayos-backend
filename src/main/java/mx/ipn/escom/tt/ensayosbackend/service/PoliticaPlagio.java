package mx.ipn.escom.tt.ensayosbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Regla de la alerta "Posible Plagio" (RN-IA-03 corregida, D4): la primera coincidencia devuelta por
 * el motor debe superar el umbral (0.96). La bandera "detectado" del motor (0.92) no se considera.
 */
@Component
public class PoliticaPlagio {

    private final BigDecimal umbral;

    public PoliticaPlagio(@Value("${app.ia.umbral-plagio:0.96}") BigDecimal umbral) {
        this.umbral = umbral;
    }

    public boolean esPlagio(JsonNode reporte) {
        JsonNode coincidencias = reporte.path("plagio").path("coincidencias");
        if (!coincidencias.isArray() || coincidencias.isEmpty()) {
            return false;
        }
        return coincidencias.get(0).path("similarity").decimalValue().compareTo(umbral) > 0;
    }

    /** Si el motor avisó plagio pero no supera el umbral, su observación sobre similitud ya no aplica. */
    public String observacion(JsonNode reporte) {
        if (!esPlagio(reporte) && motorMarcoPlagio(reporte)) {
            return null;
        }
        return reporte.path("metadata").path("observacion").asText(null);
    }

    /** La revisión docente que pidió el motor por plagio se descarta si no supera el umbral. */
    public boolean requiereRevision(JsonNode reporte) {
        boolean pedida = reporte.path("banderas_retroalimentacion").path("requiere_revision_docente").asBoolean(false);
        return esPlagio(reporte) || (pedida && !motorMarcoPlagio(reporte));
    }

    private static boolean motorMarcoPlagio(JsonNode reporte) {
        return reporte.path("plagio").path("detectado").asBoolean(false)
                || reporte.path("banderas_retroalimentacion").path("plagio_detectado").asBoolean(false);
    }
}
