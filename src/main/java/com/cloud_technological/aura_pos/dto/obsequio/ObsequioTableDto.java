package com.cloud_technological.aura_pos.dto.obsequio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObsequioTableDto {
    private Long id;
    private String sucursalNombre;
    private String terceroNombre;
    private LocalDateTime fecha;
    private String motivo;
    private BigDecimal costoTotal;
    private BigDecimal ivaTotal;
    private String estado;
    private Long totalRows;
}
