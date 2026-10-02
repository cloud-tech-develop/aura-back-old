package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Balance de prueba (Fase 4): saldo anterior, débitos, créditos y saldo final
 * por cuenta hasta el nivel pedido, con comparativo opcional. Los saldos van
 * en el signo de la naturaleza de cada cuenta (un activo con saldo débito es
 * positivo; un pasivo con saldo crédito también).
 */
@Getter
@Setter
public class BalancePruebaDto {
    private LocalDate desde;
    private LocalDate hasta;
    /** Rango del comparativo; null si no se pidió. */
    private LocalDate comparadoDesde;
    private LocalDate comparadoHasta;
    private List<Fila> filas;
    private BigDecimal totalDebitos;
    private BigDecimal totalCreditos;
    /** Débitos = créditos del período (sanidad del libro). */
    private boolean cuadra;

    @Getter
    @Setter
    public static class Fila {
        private Long cuentaId;
        private String codigo;
        private String nombre;
        private Integer nivel;
        private String naturaleza;
        private boolean auxiliar;
        private BigDecimal saldoAnterior = BigDecimal.ZERO;
        private BigDecimal debitos = BigDecimal.ZERO;
        private BigDecimal creditos = BigDecimal.ZERO;
        private BigDecimal saldoFinal = BigDecimal.ZERO;
        /** Saldo final del período comparado. */
        private BigDecimal saldoComparado;
        private BigDecimal variacion;
        /** Variación en %; null si el comparado es cero. */
        private BigDecimal variacionPct;
    }
}
