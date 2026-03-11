package com.cloud_technological.aura_pos.dto.lista_precios;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductoPrecioDto {
    @NotNull(message = "La lista de precios es obligatoria")
    private Long listaPrecioId;
    private Long productoPresentacionId; // opcional si se indica productoId
    private Long productoId;             // opcional si se indica productoPresentacionId
    @NotNull(message = "El precio es obligatorio")
    private BigDecimal precio;
    private BigDecimal utilidadEsperada;
}
