package mx.ipn.escom.tt.ensayosbackend.dto;

import java.math.BigDecimal;

/** Promedio de cada grupo del profesor, para comparar sus grupos en una gráfica. */
public record ResumenRendimientoResponse(Long grupoId, String grupo, long alumnosInscritos,
                                         long entregasCalificadas, BigDecimal promedio) {
}
