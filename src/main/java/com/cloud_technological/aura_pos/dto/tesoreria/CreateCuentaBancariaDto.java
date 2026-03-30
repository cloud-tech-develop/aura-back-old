package com.cloud_technological.aura_pos.dto.tesoreria;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCuentaBancariaDto {

    @NotBlank
    private String nombre;

    @NotBlank
    private String tipo; // BANCO | CAJA | NEQUI | DAVIPLATA | OTROS

    private String banco;
    private String numeroCuenta;
    private String titular;

    @NotNull
    private BigDecimal saldoInicial;
}
