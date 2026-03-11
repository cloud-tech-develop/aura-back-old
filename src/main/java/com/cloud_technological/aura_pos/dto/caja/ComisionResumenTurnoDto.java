package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComisionResumenTurnoDto {
    private String     tecnicoNombre;
    private Integer    totalServicios;
    private BigDecimal totalComision;
    /** SIN_LIQUIDAR | PENDIENTE | PAGADA */
    private String     estadoLiquidacion;
}
