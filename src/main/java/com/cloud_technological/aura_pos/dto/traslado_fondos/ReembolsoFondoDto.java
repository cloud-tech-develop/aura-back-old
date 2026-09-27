package com.cloud_technological.aura_pos.dto.traslado_fondos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Cuánto hay que reponer a un fondo fijo (caja menor) y con qué soportes.
 *
 * <p>El monto sugerido es {@code fondoFijo - saldoActual}: lo que devuelve el
 * fondo a su valor de constitución. No se deduce sumando gastos porque un gasto
 * anulado, una corrección o un faltante también mueven el saldo, y lo que se
 * repone es lo que falta, no lo que se documentó.
 */
@Getter
@Setter
public class ReembolsoFondoDto {
    private Long cuentaId;
    private String cuentaNombre;
    /** Suma de las constituciones vigentes: el valor fijo del fondo. */
    private BigDecimal fondoFijo;
    /** Saldo contable hoy (incluye borradores). */
    private BigDecimal saldoActual;
    /** fondoFijo - saldoActual, nunca negativo. */
    private BigDecimal montoSugerido;
    /** Desde cuándo va la relación: la última constitución o reembolso. */
    private LocalDate ultimaReposicion;
    /** Salidas del fondo desde la última reposición (la relación a legalizar). */
    private BigDecimal totalSalidas;
    private List<MovimientoFondoDto> movimientos;
}
