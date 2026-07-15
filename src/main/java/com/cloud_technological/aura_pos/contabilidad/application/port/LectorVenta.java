package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Proyección de solo lectura de la venta para contabilizarla. El generador
 * no toca entities JPA: el adapter arma este snapshot con los repos
 * existentes.
 */
public interface LectorVenta {

    VentaContable cargar(Long ventaId, Integer empresaId);

    /**
     * @param documento etiqueta del documento fiscal (" — PREF-123") o vacía
     * @param total     total a pagar (incluye impuestos, neto de descuentos)
     * @param impuestos IVA de la venta
     * @param saldoPendiente lo no pagado que va a cartera
     * @param centroCostoId  centro de costo de la sucursal (E7); null si no aplica
     * @param lineas    detalle por producto para agrupar por categoría (E4)
     */
    record VentaContable(
            LocalDate fecha,
            String documento,
            Long clienteId,
            BigDecimal total,
            BigDecimal impuestos,
            BigDecimal saldoPendiente,
            Long centroCostoId,
            List<LineaVenta> lineas,
            List<PagoVenta> pagos) {
    }

    /**
     * @param base     base gravable de la línea (subtotal − impuesto)
     * @param impuesto IVA/INC de la línea (0 si no aplica)
     * @param costo    costo de la línea (0 si no aplica)
     */
    record LineaVenta(Long productoId, BigDecimal base, BigDecimal impuesto, BigDecimal costo) {
    }

    record PagoVenta(String metodoPago, BigDecimal monto, Long cuentaBancariaId) {
    }
}
