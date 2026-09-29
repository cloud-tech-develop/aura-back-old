package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** Una línea del asiento de apertura: saldo inicial de una cuenta. */
@Getter @Setter
public class SaldoInicialLineaDto {

    @NotNull
    private Long cuentaId;

    @NotNull
    private BigDecimal debito = BigDecimal.ZERO;

    @NotNull
    private BigDecimal credito = BigDecimal.ZERO;

    /** Tercero asociado (para CxC/CxP por tercero — Fase 3). Opcional. */
    private Long terceroId;
}
