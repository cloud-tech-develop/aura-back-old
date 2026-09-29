package com.cloud_technological.aura_pos.dto.nomina.prestacion;

import java.time.LocalDate;

import lombok.Data;

/**
 * Generación masiva de una prestación (F4): prima del semestre, cesantías o
 * intereses del año para <b>todos</b> los empleados activos, en un solo lote.
 *
 * <p>Para cada empleado el "desde" se recorta a su fecha de ingreso: quien entró
 * a mitad del semestre solo causa desde que ingresó.
 */
@Data
public class GenerarLotePrestacionDto {
    /** PRIMA | CESANTIAS | INTERESES_CESANTIAS | VACACIONES. */
    private String tipo;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private String observacion;
}
