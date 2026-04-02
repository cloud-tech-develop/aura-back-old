package com.cloud_technological.aura_pos.dto.devolucion;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDevolucionDetalleDto {

    @NotNull(message = "El productoId es obligatorio")
    private Long productoId;

    private Long productoPresentacionId;

    private Long loteId;

    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;
}
