package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMovimientoDto {
    private Long sucursalId;
    private Long productoId;
    private Long loteId;
    private String tipoMovimiento;
    private BigDecimal cantidad;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoNuevo;
    private BigDecimal costoHistorico;
    private String referenciaOrigen;
}
