package com.cloud_technological.aura_pos.dto.merma;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMermaDetalleDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    private Long loteId;
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;
    @NotNull(message = "El costo unitario es obligatorio")
    private BigDecimal costoUnitario;
}
