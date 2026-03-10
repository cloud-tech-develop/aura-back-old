package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComponentePosDto {
    private Long   productoHijoId;
    private String productoHijoNombre;
    private BigDecimal cantidad;
    private String tipo; // KIT | RECETA
    private Long productoPadreId;
}
