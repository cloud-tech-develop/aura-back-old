package com.cloud_technological.aura_pos.dto.nomina.prestacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Resumen de un lote de prestaciones (V122): una fila del listado que agrupa
 * las prestaciones de una misma liquidación, con el total.
 */
@Getter
@Setter
public class LotePrestacionDto {
    private String lote;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    /** true si es una liquidación definitiva (varios conceptos + retiro). */
    private boolean definitiva;
    /** "Liquidación definitiva" o el tipo único de una individual. */
    private String tipoResumen;
    private LocalDate fecha;
    private BigDecimal total;
    /** Estado agregado del lote. */
    private String estado;
    private int cantidad;
}
