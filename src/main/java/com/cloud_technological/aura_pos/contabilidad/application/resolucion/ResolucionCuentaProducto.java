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
     * Qué hace una compra con el ítem (V185): a qué cuenta va el débito según
     * su clasificación y, para activos y diferidos, los datos de la ficha que
     * crea. Cadena: override del producto → categoría contable del mismo tipo
     * → concepto de la clasificación.
     */
    CompraProducto resolverCompra(Long productoId, Integer empresaId);

    /**
     * @param esServicio true → la línea no genera par costo/inventario
     * @param devolucionId cuenta de devolución en ventas; si la categoría no
     *                     la define, es la misma de ingreso
     */
    /**
     * @param cuentaCompraId          débito de la línea de compra
     * @param cuentaDepreciacionId    null si la categoría no la trae
     * @param vidaUtilMeses           null si la categoría no la trae
     */
    record CompraProducto(
            com.cloud_technological.aura_pos.utils.ClasificacionItem clasificacion,
            Long cuentaCompraId,
            Long cuentaDepreciacionId,
            Long cuentaGastoDepreciacionId,
            Integer vidaUtilMeses,
            Integer mesesDiferido,
            Long cuentaGastoId) {
    }

    record CuentasProducto(
            Long ingresoId,
            Long costoId,
            Long inventarioId,
            Long devolucionId,
            boolean esServicio) {
    }
}
