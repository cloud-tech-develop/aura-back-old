package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaResumenDto {
    private BigDecimal saldoTotal;
    private BigDecimal saldoVencido;
    private BigDecimal saldoPorVencer;
    private Integer facturasAbiertas;
    private Integer facturasVencidas;
    private Integer diasMoraMaximo;
    /** Cupo menos saldo; null si el cliente no tiene crédito configurado. */
    private BigDecimal cupoDisponible;
    private BigDecimal anticipoDisponible;
    private BigDecimal recaudado90Dias;
    /** Días promedio entre la emisión y el último abono, facturas pagadas del último año. */
    private Integer promedioDiasPago;
    private LocalDateTime ultimoPagoFecha;
    private BigDecimal ultimoPagoMonto;
    // Edades del saldo
    private BigDecimal edadPorVencer;
    private BigDecimal edad1a30;
    private BigDecimal edad31a60;
    private BigDecimal edad61a90;
    private BigDecimal edadMas90;
}
