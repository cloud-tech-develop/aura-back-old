package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Una fila por RECETA (no por ingrediente).
 *
 * El listado plano de líneas era ilegible en cuanto un negocio tenía 60
 * productos compuestos: 60 recetas × 8 ingredientes = 480 filas sueltas.
 */
@Getter
@Setter
public class RecetaResumenTableDto {
    private Long productoPadreId;
    private String productoPadreNombre;
    private String productoPadreSku;
    private String tipo;
    private BigDecimal rendimiento;
    private Integer totalComponentes;

    /**
     * Costo por unidad sumando `producto.costo` de cada componente.
     * Es plano: no explota subrecetas (para eso está el endpoint de costeo).
     */
    private BigDecimal costoEstimado;

    private BigDecimal costoActual;
    private BigDecimal precio;

    private long totalRows;
}
