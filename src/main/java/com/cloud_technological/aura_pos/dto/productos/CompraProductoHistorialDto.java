package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Una línea de compra (o nota crédito) del producto. Se lee directo de
 * {@code compra_detalle}, así que incluye las compras anteriores al historial
 * de costos.
 */
@Getter
@Setter
public class CompraProductoHistorialDto {
    private Long compraId;
    private String numeroCompra;
    private LocalDateTime fecha;
    private String tipoDocumento;
    private String estado;
    private String proveedorNombre;
    /** Unidades base. */
    private BigDecimal cantidad;
    /** Costo unitario antes de descuento, por unidad base. */
    private BigDecimal costoUnitario;
    private BigDecimal descuentoPct;
    /** Costo unitario neto de descuento (sin fletes ni IVA). */
    private BigDecimal costoNeto;
    private BigDecimal subtotalLinea;
    private String presentacionNombre;
    private BigDecimal cantidadPresentacion;
    private BigDecimal costoPresentacion;
}
