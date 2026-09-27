package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Producto para operaciones de inventario (merma, obsequio). A diferencia del
 * catálogo del POS, incluye insumos y productos ocultos del POS.
 */
@Getter
@Setter
public class ProductoInventarioDto {
    private Long id;
    private String nombre;
    private String sku;
    private String codigoBarras;
    private BigDecimal stockActual;
    private BigDecimal costo;
    private BigDecimal precio;
    private BigDecimal ivaPorcentaje;
    private Boolean manejaLotes;
    private Boolean manejaSerial;
    private Boolean manejaInventario;
    private Boolean permitirStockNegativo;
    /** Tiene receta: lo que sale del inventario son sus componentes. */
    private Boolean esCompuesto;
    private String unidadAbreviatura;
    private String usoProducto;
}
