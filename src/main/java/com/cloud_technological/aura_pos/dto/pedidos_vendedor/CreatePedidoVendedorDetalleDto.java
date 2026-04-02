package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class CreatePedidoVendedorDetalleDto {

    @NotNull
    private Long productoId;

    @NotNull
    @DecimalMin(value = "0.001")
    private BigDecimal cantidad;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal precioUnitario;

    private BigDecimal descuentoValor;

    private BigDecimal impuestoValor;
}
