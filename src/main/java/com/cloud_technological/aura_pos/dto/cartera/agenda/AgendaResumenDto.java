package com.cloud_technological.aura_pos.dto.cartera.agenda;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgendaResumenDto {
    private Integer promesasHoy;
    private BigDecimal montoPromesasHoy;
    private Integer promesasIncumplidas;
    private BigDecimal montoPromesasIncumplidas;
    private Integer facturasPorVencer;
    private BigDecimal saldoPorVencer;
    private Integer clientesSinGestion;
    private BigDecimal saldoSinGestion;
    /** Promesas con fecha en el mes: cumplidas contra el total resuelto. */
    private Integer promesasCumplidasMes;
    private Integer promesasResueltasMes;
}
