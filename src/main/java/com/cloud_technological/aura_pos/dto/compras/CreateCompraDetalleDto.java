package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCompraDetalleDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;
    @NotNull(message = "El costo unitario es obligatorio")
    private BigDecimal costoUnitario;
    private BigDecimal impuestoValor = BigDecimal.ZERO;
    private BigDecimal descuentoPct = BigDecimal.ZERO;
    private BigDecimal precioVenta1;
    private BigDecimal precioVenta2;
    private BigDecimal precioVenta3;
}
