package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AbrirTurnoDto {
    @NotNull(message = "La caja es obligatoria")
    private Long cajaId;
    @NotNull(message = "La base inicial es obligatoria")
    private BigDecimal baseInicial;
}
