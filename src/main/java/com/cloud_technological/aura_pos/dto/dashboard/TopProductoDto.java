package com.cloud_technological.aura_pos.dto.dashboard;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopProductoDto {
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private BigDecimal totalVendido;
    private BigDecimal totalIngresos;
}
