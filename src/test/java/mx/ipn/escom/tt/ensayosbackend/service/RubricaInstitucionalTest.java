package mx.ipn.escom.tt.ensayosbackend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static mx.ipn.escom.tt.ensayosbackend.service.RubricaInstitucional.Nivel.*;
import static org.assertj.core.api.Assertions.assertThat;

class RubricaInstitucionalTest {

    // Nombres tal como los devuelve el microservicio de IA real
    @ParameterizedTest
    @ValueSource(strings = {"Formato de Archivo", "Estructura Documental", "Ortografía y Sintaxis",
            "Título del Ensayo", "Introducción", "Desarrollo", "Conclusiones", "Referencias"})
    void cadaCriterioTieneDescripcionEnLosTresNiveles(String criterio) {
        String alto = RubricaInstitucional.descripcion(criterio, ALTO);
        String medio = RubricaInstitucional.descripcion(criterio, MEDIO);
        String bajo = RubricaInstitucional.descripcion(criterio, BAJO);
        assertThat(alto).isNotBlank();
        assertThat(medio).isNotBlank();
        assertThat(bajo).isNotBlank();
        assertThat(alto).isNotEqualTo(medio).isNotEqualTo(bajo);
    }

    @Test
    void toleraAcentosYMayusculasDistintos() {
        assertThat(RubricaInstitucional.descripcion("titulo  DEL ensayo", ALTO))
                .isEqualTo(RubricaInstitucional.descripcion("Título del Ensayo", ALTO));
        assertThat(RubricaInstitucional.descripcion("Creatividad", ALTO)).isNull();
    }

    @Test
    void nivelSegunPorcentaje() {
        assertThat(RubricaInstitucional.nivel(new BigDecimal("1.0"), new BigDecimal("1.0"))).isEqualTo(ALTO);
        assertThat(RubricaInstitucional.nivel(new BigDecimal("0.25"), new BigDecimal("0.5"))).isEqualTo(MEDIO);
        assertThat(RubricaInstitucional.nivel(new BigDecimal("3.0"), new BigDecimal("4.0"))).isEqualTo(MEDIO);
        assertThat(RubricaInstitucional.nivel(new BigDecimal("0.00"), new BigDecimal("1.5"))).isEqualTo(BAJO);
    }
}
