package mx.ipn.escom.tt.ensayosbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rendimiento de un grupo para las gráficas del profesor. Los promedios son sobre las entregas
 * calificadas (CALIFICADO y POSIBLE_PLAGIO, con el ajuste del docente si lo hubo) y valen null si no hay.
 */
public record RendimientoGrupoResponse(
        Long grupoId,
        String grupo,
        long alumnosInscritos,
        long entregasCalificadas,
        BigDecimal promedioGeneral,
        /** Cuántas entregas caen en cada rango de calificación, para un histograma. */
        List<Rango> distribucion,
        /** Promedio por tarea, de la que cierra primero a la última (evolución del grupo). */
        List<PorTarea> tareas,
        /** Todos los inscritos, ordenados por apellidos; promedio null si no tiene entregas calificadas. */
        List<PorAlumno> alumnos,
        /** Promedio de cada criterio de la rúbrica, en puntos y en porcentaje de su máximo. */
        List<PorCriterio> criterios) {

    public record Rango(String rango, BigDecimal desde, BigDecimal hasta, long entregas) {
    }

    public record PorTarea(Long tareaId, String tarea, LocalDateTime fechaCierre, long entregasCalificadas,
                           BigDecimal promedio, BigDecimal minima, BigDecimal maxima) {
    }

    public record PorAlumno(Long alumnoId, String nombre, String apellidos, long entregasCalificadas,
                            BigDecimal promedio) {
    }

    public record PorCriterio(String criterio, BigDecimal promedio, BigDecimal puntajeMaximo,
                              BigDecimal porcentaje) {
    }
}
