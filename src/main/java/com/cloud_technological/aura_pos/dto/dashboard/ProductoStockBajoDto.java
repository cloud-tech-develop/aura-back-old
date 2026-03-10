package com.cloud_technological.aura_pos.dto.dashboard;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoStockBajoDto {
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private Long sucursalId;
    private String sucursalNombre;
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
}
