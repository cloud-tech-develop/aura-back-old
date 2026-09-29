package com.cloud_technological.aura_pos.contabilidad.application.resolucion;

/**
 * Puerto de resolución de la cuenta del impuesto de un producto (E5):
 * impuesto del producto → cuenta generado (ventas) / descontable (compras);
 * fallback a los conceptos IVA_GENERADO (240801) / IVA_DESCONTABLE (240802).
 */
public interface ResolucionImpuesto {

    /** Cuenta del impuesto GENERADO (ventas) para el producto. */
    Long resolverGenerado(Long productoId, Integer empresaId);

    /** Cuenta del impuesto DESCONTABLE (compras) para el producto. */
    Long resolverDescontable(Long productoId, Integer empresaId);
}
