package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTerceroCreditoDto {

    @NotNull(message = "El tercero es obligatorio")
    private Long terceroId;

    @NotNull(message = "El cupo inicial es obligatorio")
    @Min(value = 0, message = "El cupo debe ser mayor o igual a 0")
    private BigDecimal cupoCreditoInicial;

    @Min(value = 1, message = "El plazo debe ser al menos 1 día")
    private Integer plazoDias;

    private String  estadoCredito;       // default ACTIVO
    private Boolean requiereAutorizacion;
    private Integer diasMoraTolerancia;  // default 30
}
