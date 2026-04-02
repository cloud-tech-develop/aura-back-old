package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlujoCajaDto {
    private String desde;
    private String hasta;

    /** Saldo acumulado de tesorería antes del período */
    private BigDecimal saldoInicial;

    /** Movimientos reales de tesorería en el período */
    private List<FlujoCajaLineaDto> movimientos;

    private BigDecimal totalIngresos;
    private BigDecimal totalEgresos;

    /** saldoInicial + totalIngresos - totalEgresos */
    private BigDecimal saldoFinal;

    /** Cuentas por cobrar activas (proyección de entradas) */
    private List<FlujoCajaProyeccionDto> proyeccionCxC;

    /** Cuentas por pagar activas (proyección de salidas) */
    private List<FlujoCajaProyeccionDto> proyeccionCxP;

    private BigDecimal totalPorCobrar;
    private BigDecimal totalPorPagar;
}
