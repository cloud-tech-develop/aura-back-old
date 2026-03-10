package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateInventarioDto {
    private BigDecimal stockMinimo;
    private BigDecimal stockActual;
    private String ubicacion;
}