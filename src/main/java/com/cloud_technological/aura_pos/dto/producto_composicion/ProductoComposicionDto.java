package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoComposicionDto {
    private Long id;
    private Long productoPadreId;
    private String productoPadreNombre;
    private Long productoHijoId;
    private String productoHijoNombre;
    private BigDecimal cantidad;
    private String tipo; // KIT, RECETA
}
