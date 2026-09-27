package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Presentación a la venta de un producto en el POS (Paca ×25 a $58.000). */
@Getter
@Setter
public class PresentacionPosDto {
    private Long productoId;
    private Long id;
    private String nombre;
    private String codigoBarras;
    /** Precio de la presentación, con IVA incluido. */
    private BigDecimal precio;
    /** Unidades base que contiene. */
    private BigDecimal factorConversion;
    private Boolean esDefaultVenta;
    /** Presentaciones completas que alcanza el stock de la sucursal. */
    private BigDecimal stock;
}
