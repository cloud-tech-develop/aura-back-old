package com.cloud_technological.aura_pos.dto.cuentas_pagar;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuentaPagarResumenDto {
    private Long totalCuentas;
    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;
    private Long cantidadActivas;
    private Long cantidadPagadas;
    private Long cantidadVencidas;
}
