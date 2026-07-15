package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyección de solo lectura de los abonos (cobro de cartera / pago a
 * proveedor) para contabilizarlos. Las entities JPA no salen del adapter.
 */
public interface LectorAbonos {

    /** Abono a cuenta por cobrar (recaudo de cartera). */
    AbonoContable cargarCobro(Long abonoId, Integer empresaId);

    /** Abono a cuenta por pagar (pago a proveedor). */
    AbonoContable cargarPago(Long abonoId, Integer empresaId);

    /**
     * @param fecha fecha contable del abono (hoy, como el flujo legacy)
     * @param cuentaBancariaId cuenta bancaria del pago; null en cobros de caja
     */
    record AbonoContable(
            LocalDate fecha,
            BigDecimal monto,
            Long terceroId,
            String metodoPago,
            Long cuentaBancariaId) {
    }
}
