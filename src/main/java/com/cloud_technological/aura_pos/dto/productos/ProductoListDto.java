package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoListDto {
    private Long id;
    private String sku;
    private String nombre;
    private BigDecimal costo;
    private BigDecimal precio;
    private BigDecimal precio2;
    private BigDecimal precio3;
    private BigDecimal ivaPorcentaje;
    private String tipoProducto;
}
