package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Saldo del mayor de una cuenta del disponible (11xx: caja/bancos) a una fecha,
 * para mostrar la posición de efectivo real en el cierre contable.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaldoDisponibleDto {
    private String codigo;
    private String nombre;
    private BigDecimal saldo;
}
