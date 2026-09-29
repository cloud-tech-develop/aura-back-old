package com.cloud_technological.aura_pos.dto.nomina.empleado;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * Saldos iniciales de un empleado (F7): lo que trae de su sistema anterior y el
 * motor no puede recalcular. Sirve como fila de la pantalla de carga y como
 * cuerpo de la actualización.
 */
@Data
public class SaldosInicialesDto {
    private Long empleadoId;
    private String empleadoNombre;
    private String documento;
    private LocalDate fechaIngreso;
    private BigDecimal vacacionesSaldoInicial;
    private BigDecimal cesantiasSaldoInicial;
    private BigDecimal ingresosYtd;
    private BigDecimal retencionesYtd;
}
