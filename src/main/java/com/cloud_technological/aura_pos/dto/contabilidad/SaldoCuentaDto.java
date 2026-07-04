package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Saldo acumulado (débito/crédito) de una cuenta en un período. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaldoCuentaDto {
    private Long cuentaId;
    private BigDecimal debito;
    private BigDecimal credito;
}
