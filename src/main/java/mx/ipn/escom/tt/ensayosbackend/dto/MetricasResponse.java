package mx.ipn.escom.tt.ensayosbackend.dto;

/** Métricas del panel del administrador (sección 4.5.5). Solo conteos, sin datos académicos. */
public record MetricasResponse(long alumnos, long profesores, long cuentasActivas, long cuentasSuspendidas,
                               long gruposActivos, long ensayosProcesados, long ensayosEnRevision) {
}
