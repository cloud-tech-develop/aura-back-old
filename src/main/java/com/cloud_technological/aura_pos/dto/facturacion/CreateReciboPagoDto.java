package com.cloud_technological.aura_pos.dto.facturacion;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReciboPagoDto {
    
    @NotNull(message = "El ID de la factura es obligatorio")
    private Long facturaId;

    @NotNull(message = "El valor es obligatorio")
    @Positive(message = "El valor debe ser mayor a cero")
    private BigDecimal valor;

    private String banco;

    private String tipo;

    private String descripcion;

    @NotNull(message = "El método de pago es obligatorio")
    private String metodoPago;
}
