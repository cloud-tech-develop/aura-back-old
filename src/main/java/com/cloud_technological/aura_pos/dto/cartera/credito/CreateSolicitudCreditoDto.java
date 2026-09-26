package com.cloud_technological.aura_pos.dto.cartera.credito;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSolicitudCreditoDto {
    private Long terceroId;
    private BigDecimal monto;
    private String observacion;
}
