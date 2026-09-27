package com.cloud_technological.aura_pos.dto.consumo_interno;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsumoInternoTableDto {
    private Long id;
    private String sucursalNombre;
    private String conceptoNombre;
    private String responsableNombre;
    private LocalDateTime fecha;
    private BigDecimal costoTotal;
    private BigDecimal ivaTotal;
    private String estado;
    private Long totalRows;
}
