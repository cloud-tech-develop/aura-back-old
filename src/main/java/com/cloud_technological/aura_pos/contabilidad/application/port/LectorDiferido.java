package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyección de la cuota de amortización de un gasto diferido (E6). */
public interface LectorDiferido {

    CuotaDiferido cargar(Long amortizacionId, Integer empresaId);

    /** @param cuentaGastoId cuenta de gasto del documento; null → gasto general */
    record CuotaDiferido(
            Long gastoId,
            String periodo,
            LocalDate fecha,
            BigDecimal monto,
            Long cuentaGastoId,
            Long terceroId,
            Long centroCostoId) {
    }
}
