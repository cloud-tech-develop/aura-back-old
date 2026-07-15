package com.cloud_technological.aura_pos.contabilidad.application.resolucion;

/**
 * Puerto de resolución de cuentas por producto (E4), cadena de
 * responsabilidad: override del producto → categoría contable →
 * concepto de la empresa (comportamiento actual). Los generadores agrupan
 * las líneas por estas cuentas.
 */
public interface ResolucionCuentaProducto {

    CuentasProducto resolver(Long productoId, Integer empresaId);

    /**
     * @param esServicio true → la línea no genera par costo/inventario
     * @param devolucionId cuenta de devolución en ventas; si la categoría no
     *                     la define, es la misma de ingreso
     */
    record CuentasProducto(
            Long ingresoId,
            Long costoId,
            Long inventarioId,
            Long devolucionId,
            boolean esServicio) {
    }
}
