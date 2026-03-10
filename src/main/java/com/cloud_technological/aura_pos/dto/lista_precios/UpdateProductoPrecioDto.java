package com.cloud_technological.aura_pos.dto.lista_precios;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProductoPrecioDto {
    @NotNull(message = "El precio es obligatorio")
    private BigDecimal precio;
    private BigDecimal utilidadEsperada;
}
