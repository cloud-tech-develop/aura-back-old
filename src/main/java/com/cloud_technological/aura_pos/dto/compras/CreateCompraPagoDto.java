package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCompraPagoDto {
    @NotBlank(message = "El método de pago es obligatorio")
    private String metodoPago; // EFECTIVO, TARJETA, TRANSFERENCIA, CREDITO
    @NotNull(message = "El monto es obligatorio")
    private BigDecimal monto;
    private String banco;
    private String referencia;
}
