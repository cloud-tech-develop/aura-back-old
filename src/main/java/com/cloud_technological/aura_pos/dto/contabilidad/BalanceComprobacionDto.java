package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BalanceComprobacionDto {
    private Long periodoId;
    private Short anio;
    private Short mes;
    private String estadoPeriodo;
    private List<BalanceComprobacionLineaDto> lineas;
    private BigDecimal totalDebito;
    private BigDecimal totalCredito;
    /** true si totalDebito == totalCredito (partida doble cuadrada) */
    private boolean cuadrado;
}
