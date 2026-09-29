package com.cloud_technological.aura_pos.dto.tesoreria;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * Conciliación entre el saldo que lleva tesorería para una cuenta bancaria y el
 * saldo contable de su cuenta del mayor (11xx). La diferencia debe ser 0 cuando
 * todo movimiento bancario está contabilizado.
 */
@Data
@Builder
public class ConciliacionMayorDto {
    private Long cuentaBancariaId;
    private String cuentaBancariaNombre;
    private Long cuentaContableId;
    private String cuentaContableCodigo;
    private String cuentaContableNombre;
    private BigDecimal saldoTesoreria;   // cuenta_bancaria.saldo_actual
    private BigDecimal saldoMayor;       // saldo del mayor de la cuenta contable
    private BigDecimal diferencia;       // saldoTesoreria - saldoMayor
}
