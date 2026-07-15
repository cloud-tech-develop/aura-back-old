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

    /** El banco como tercero (persona jurídica). Opcional. */
    private Long terceroId;

    /** Cuenta contable del PUC asociada (1110xx). Opcional pero recomendado. */
    private Long cuentaContableId;

    @NotNull
    private BigDecimal saldoInicial;

    // Sobregiro (E2): saldo negativo permitido hasta el cupo.
    private Boolean permiteSobregiro;
    private BigDecimal cupoSobregiro;
}
