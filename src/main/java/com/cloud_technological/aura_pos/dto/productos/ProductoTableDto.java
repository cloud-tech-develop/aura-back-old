package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoTableDto {
    private Long id;
    private String sku;
    private String nombre;
    private String codigoBarras;
    private String categoriaNombre;
    private String marcaNombre;
    private String tipoProducto;
    private String usoProducto;
    private BigDecimal precio;
    private BigDecimal costo;
    private Boolean activo;
    private BigDecimal ivaPorcentaje;
    /** Abreviatura de la unidad de inventario (kg, und): rotula la conversión de las presentaciones. */
    private String unidadAbreviatura;
    private Boolean manejaLotes;
    private Boolean manejaSerial;
    private long totalRows;
}
