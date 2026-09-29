package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepreciacionPeriodoDto {
    private Long id;
    private Long activoId;
    private String activoDescripcion;
    private Long periodoId;
    private BigDecimal valor;
    private Long asientoId;
    private LocalDateTime calculadoEn;
}
