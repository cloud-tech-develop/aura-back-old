package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Aporte de un componente al costo del producto compuesto. */
@Getter
@Setter
public class RecetaCosteoLineaDto {
    private Long productoHijoId;
    private String productoHijoNombre;

    /** Consumo por 1 unidad del padre, en unidad base del hijo. */
    private BigDecimal cantidad;

    /** Consumo del lote completo. */
    private BigDecimal cantidadLote;

    /** Costo de 1 unidad base del componente. */
    private BigDecimal costoUnitario;

    /** cantidad × costoUnitario. */
    private BigDecimal costoTotal;

    /** Peso de esta línea en el costo del producto, en %. */
    private BigDecimal participacion;

    /**
     * true si el costo salió de explotar la receta del propio componente
     * (subreceta) y no de su campo `costo`.
     */
    private Boolean costoDerivadoDeReceta;

    /** Se llena cuando el componente no tiene costo cargado. */
    private String advertencia;
}
