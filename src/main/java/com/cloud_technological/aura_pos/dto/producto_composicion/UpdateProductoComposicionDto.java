package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProductoComposicionDto {
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;
    @NotBlank(message = "El tipo es obligatorio")
    private String tipo;
}
