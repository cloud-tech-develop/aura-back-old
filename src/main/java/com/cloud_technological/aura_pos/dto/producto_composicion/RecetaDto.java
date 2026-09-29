package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Receta completa de un producto: cabecera + líneas. */
@Getter
@Setter
public class RecetaDto {
    private Long productoPadreId;
    private String productoPadreNombre;
    private String tipo;

    /** Unidades que salen de un lote. */
    private BigDecimal rendimiento;

    private String unidadBaseAbreviatura;

    private List<RecetaComponenteDetalleDto> componentes;
}
