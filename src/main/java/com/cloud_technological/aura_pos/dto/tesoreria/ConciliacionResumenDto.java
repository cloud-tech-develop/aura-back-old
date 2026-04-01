package com.cloud_technological.aura_pos.dto.tesoreria;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConciliacionResumenDto {
    private Long cuentaId;
    private String cuentaNombre;
    private BigDecimal saldoContable;
    private Long totalMovimientos;
    private Long movimientosConciliados;
    private Long movimientosPendientes;
    private BigDecimal totalEntradasConciliadas;
    private BigDecimal totalSalidasConciliadas;
    private BigDecimal totalEntradasPendientes;
    private BigDecimal totalSalidasPendientes;
}
