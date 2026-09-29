package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Costeo de un producto compuesto a partir de su receta.
 *
 * Es la respuesta a "¿cuánto me cuesta producir un pan?": suma el costo de cada
 * componente y lo compara contra el precio de venta.
 */
@Getter
@Setter
public class RecetaCosteoDto {
    private Long productoPadreId;
    private String productoPadreNombre;
    private BigDecimal rendimiento;

    private List<RecetaCosteoLineaDto> lineas;

    /** Costo de producir el lote completo. */
    private BigDecimal costoLote;

    /** Costo de 1 unidad del producto. Es el que sugiere guardar en `producto.costo`. */
    private BigDecimal costoUnitario;

    /** Costo hoy registrado en el producto, para comparar. */
    private BigDecimal costoActual;

    private BigDecimal precioVenta;

    /** precioVenta − costoUnitario. */
    private BigDecimal utilidadUnitaria;

    /** Utilidad sobre precio de venta, en %. Null si no hay precio. */
    private BigDecimal margenPorcentaje;

    /** true si algún componente no tenía costo: el total quedó subestimado. */
    private Boolean costoIncompleto;
}
