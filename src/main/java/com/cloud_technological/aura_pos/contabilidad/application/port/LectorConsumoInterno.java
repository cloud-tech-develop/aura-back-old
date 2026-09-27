package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Proyección del consumo interno para contabilizarlo: el costo que sale de
 * inventario hacia la cuenta del concepto y, si aplica, el IVA por retiro.
 */
public interface LectorConsumoInterno {

    ConsumoInternoContable cargar(Long consumoInternoId, Integer empresaId);

    /**
     * @param cuentaGastoId cuenta del concepto; null = la de la configuración contable
     * @param terceroId     responsable (puede ser null)
     */
    record ConsumoInternoContable(
            LocalDate fecha,
            String concepto,
            Long cuentaGastoId,
            Long terceroId,
            Long centroCostoId,
            boolean generaIva,
            List<LineaConsumoInterno> lineas) {
    }

    record LineaConsumoInterno(Long productoId, BigDecimal costo, BigDecimal iva) {
    }
}
