package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCompraDetalleDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    /** Si viene, cantidad y costoUnitario están en esa presentación (4 Pacas a $52.500). */
    private Long productoPresentacionId;
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;

    /** Compra de un producto con serial: uno por unidad (en unidad base). */
    private java.util.List<String> seriales;

    /** Nota crédito de un producto con serial: los que se devuelven al proveedor. */
    private java.util.List<Long> serialIds;
    @NotNull(message = "El costo unitario es obligatorio")
    private BigDecimal costoUnitario;
    private BigDecimal impuestoValor = BigDecimal.ZERO;
    private BigDecimal descuentoPct = BigDecimal.ZERO;
    private BigDecimal precioVenta1;
    private BigDecimal precioVenta2;
    private BigDecimal precioVenta3;

    /**
     * Lotes de la línea si el producto maneja lotes. En compra, la suma tiene
     * que ser la cantidad de la línea (si no viene ninguno, entra a SIN-LOTE).
     * En nota crédito, si no viene ninguno, sale primero el que vence antes.
     */
    @javax.validation.Valid
    private java.util.List<CreateCompraDetalleLoteDto> lotes;
}
