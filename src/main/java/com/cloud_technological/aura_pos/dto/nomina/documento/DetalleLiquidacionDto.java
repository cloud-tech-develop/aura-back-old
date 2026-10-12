package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Línea del detalle por concepto de un empleado liquidado, con su traza. */
@Getter
@Setter
public class DetalleLiquidacionDto {
    private Long id;
    private String conceptoCodigo;
    private String conceptoNombre;
    /** DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION. */
    private String clase;
    private BigDecimal cantidad;
    private BigDecimal base;
    private BigDecimal porcentaje;
    private BigDecimal valor;
    /** JSON [{paso, valor}] tal como lo guardó el motor. */
    private String traza;
}
