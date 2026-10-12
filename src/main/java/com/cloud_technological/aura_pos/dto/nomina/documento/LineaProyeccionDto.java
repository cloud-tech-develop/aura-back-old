package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Línea de la proyección contable, agrupada por cuenta y descripción. */
@Getter
@Setter
public class LineaProyeccionDto {
    private Long cuentaId;
    private String cuentaCodigo;
    private String cuentaNombre;
    private String descripcion;
    /** A nombre de quién va la línea (EPS, AFP, DIAN, el empleado…); null = sin tercero. */
    private Long terceroId;
    private String terceroNombre;
    private BigDecimal debito = BigDecimal.ZERO;
    private BigDecimal credito = BigDecimal.ZERO;
}
