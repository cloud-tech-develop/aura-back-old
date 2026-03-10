package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CerrarTurnoDto {
    @NotNull(message = "El efectivo real es obligatorio")
    private BigDecimal totalEfectivoReal;
}
