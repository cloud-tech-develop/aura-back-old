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
     * @param cuentaContableId cuenta elegida a mano (V142); manda sobre el
     *                         resto cuando el movimiento no pasa por caja ni
     *                         por un banco
     */
    record AbonoContable(
            LocalDate fecha,
            BigDecimal monto,
            Long terceroId,
            String metodoPago,
            Long cuentaBancariaId,
            Long cuentaContableId,
            java.util.List<Retencion> retenciones) {

        public AbonoContable(LocalDate fecha, BigDecimal monto, Long terceroId, String metodoPago,
                Long cuentaBancariaId, Long cuentaContableId) {
            this(fecha, monto, terceroId, metodoPago, cuentaBancariaId, cuentaContableId,
                    java.util.List.of());
        }
    }

    /** Retención que el cliente practicó sobre el abono (RETEFUENTE | RETEIVA | RETEICA). */
    record Retencion(String tipo, BigDecimal valor) {
    }
}
