package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyección del cierre de turno de caja para contabilizar su diferencia.
 * {@code diferencia} = efectivo real − esperado: negativo es faltante,
 * positivo es sobrante.
 */
public interface LectorDiferenciaCaja {

    DiferenciaCaja cargar(Long turnoId, Integer empresaId);

    record DiferenciaCaja(LocalDate fecha, BigDecimal diferencia) {
    }
}
