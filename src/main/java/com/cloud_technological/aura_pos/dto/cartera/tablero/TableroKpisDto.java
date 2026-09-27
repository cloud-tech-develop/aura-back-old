package com.cloud_technological.aura_pos.dto.cartera.tablero;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableroKpisDto {
    private BigDecimal saldoTotal;
    private BigDecimal saldoTotalAnterior;
    private BigDecimal vencido;
    /** Porcentaje del saldo que está vencido. */
    private BigDecimal pctVencido;
    private BigDecimal pctVencidoAnterior;
    /** Días promedio de cobro: saldo / ventas a crédito de 90 días × 90. */
    private Integer dso;
    private Integer dsoAnterior;
    private BigDecimal recaudoMes;
    private BigDecimal recaudoMesAnterior;
    /** Lo cobrado de lo que vencía en el mes, en porcentaje. */
    private BigDecimal efectividadMes;
    private BigDecimal efectividadMesAnterior;
    private Integer clientesConSaldo;
    private Integer clientesEnMora;
    private Integer promesasCumplidasMes;
    private Integer promesasResueltasMes;
    /** Participación de los 5 mayores deudores en el saldo total. */
    private BigDecimal concentracionTop5;
}
