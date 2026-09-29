package com.cloud_technological.aura_pos.dto.cartera.tablero;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Foto de la cartera al cierre de un mes (o a hoy, en el mes en curso). */
@Getter
@Setter
public class TableroMesDto {
    private LocalDate mes;
    private LocalDate corte;
    private BigDecimal saldoTotal;
    private BigDecimal alDia;
    private BigDecimal dias1a30;
    private BigDecimal dias31a60;
    private BigDecimal dias61a90;
    private BigDecimal mas90;
    private BigDecimal vencido;
    private BigDecimal recaudado;
    private BigDecimal ventasCredito;
    private BigDecimal ventas90;
    /** Saldo, al empezar el mes, de lo que vencía hasta fin de mes. */
    private BigDecimal cobrable;
    /** Lo pagado en el mes sobre ese cobrable. */
    private BigDecimal recaudoCobrable;
    private Integer dso;
    private BigDecimal efectividad;
}
