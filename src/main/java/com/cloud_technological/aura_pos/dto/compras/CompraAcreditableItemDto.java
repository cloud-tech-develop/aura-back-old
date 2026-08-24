package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo que queda por acreditar de un producto en una factura de compra: lo que
 * trajo la factura menos lo que ya se llevaron las notas crédito anteriores.
 */
@Getter
@Setter
public class CompraAcreditableItemDto {
    private Long productoId;
    private String productoNombre;
    private String productoSku;

    /** Cantidad que aún se puede acreditar. */
    private BigDecimal cantidadDisponible;

    /** El costo al que se compró: la nota crédito devuelve a ese precio. */
    private BigDecimal costoUnitario;

    /** IVA de la línea original, para reconstruirlo en la nota crédito. */
    private BigDecimal ivaPct;

    /** Descuento de la línea original: la NC acredita lo que se cobró, no el bruto. */
    private BigDecimal descuentoPct;
}
