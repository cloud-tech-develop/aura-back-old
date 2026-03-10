package com.cloud_technological.aura_pos.dto.dashboard;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResumenVentasDto {
    private BigDecimal total;
    private Long cantidad;
    private BigDecimal promedio;
}
