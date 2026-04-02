package com.cloud_technological.aura_pos.dto.ventas;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class CreateVentaPagoDto {
    @NotBlank(message = "El método de pago es obligatorio")
    private String metodoPago; // EFECTIVO, TARJETA, NEQUI, TRANSFERENCIA, DAVIPLATA, CREDITO
    @NotNull(message = "El monto es obligatorio")
    private BigDecimal monto;
    private String referencia;
    private Long cuentaBancariaId;
}
