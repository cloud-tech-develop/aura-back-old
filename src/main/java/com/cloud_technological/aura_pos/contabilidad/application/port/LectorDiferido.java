package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyección de la cuota de amortización de un diferido (de gasto E6 o de compra V185). */
public interface LectorDiferido {

    CuotaDiferido cargar(Long amortizacionId, Integer empresaId);

    /**
     * @param origen           texto del documento que lo creó ("gasto #12", "compra #40")
     * @param cuentaGastoId    cuenta de gasto del mes; null → gasto general
     * @param cuentaDiferidoId cuenta que se debitó al crearlo; null → 1705
     */
    record CuotaDiferido(
            String origen,
            String periodo,
            LocalDate fecha,
            BigDecimal monto,
            Long cuentaGastoId,
            Long cuentaDiferidoId,
            Long terceroId,
            Long centroCostoId) {
    }
}
