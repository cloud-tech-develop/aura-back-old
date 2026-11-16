package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteMargenesProductoDto {
    private Long productoId;
    private String productoNombre;
    private String sku;
    private String categoriaNombre;
    private BigDecimal precioVenta;
    private BigDecimal costo;
    private BigDecimal margenBruto;
    private BigDecimal margenPorcentaje;
    private BigDecimal cantidadVendida;
    /** Total cobrado con IVA. */
    private BigDecimal ingresoTotal;
    /** Venta sin IVA, después de descuentos. */
    private BigDecimal ventaNeta;
    /** Costo congelado al vender (venta_detalle.costo_linea). */
    private BigDecimal costoTotal;
    /** ventaNeta − costoTotal. */
    private BigDecimal utilidad;
}
