package com.cloud_technological.aura_pos.dto.tesoreria;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaBancariaDto {
    private Long id;
    private String nombre;
    private String tipo;
    private String banco;
    private String numeroCuenta;
    private String titular;
    private BigDecimal saldoInicial;
    private BigDecimal saldoActual;
    private Boolean activa;
}
