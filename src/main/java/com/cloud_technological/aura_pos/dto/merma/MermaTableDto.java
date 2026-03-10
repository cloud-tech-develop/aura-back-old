package com.cloud_technological.aura_pos.dto.merma;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermaTableDto {
    private Long id;
    private String sucursalNombre;
    private String motivoNombre;
    private LocalDateTime fecha;
    private BigDecimal costoTotal;
    private String estado;
    private long totalRows;
}
