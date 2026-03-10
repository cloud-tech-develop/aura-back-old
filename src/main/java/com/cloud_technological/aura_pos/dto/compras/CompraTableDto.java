package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraTableDto {
    private Long id;
    private String numeroCompra;
    private String proveedorNombre;
    private String sucursalNombre;
    private LocalDateTime fecha;
    private BigDecimal total;
    private String estado;
    private long totalRows;
}
