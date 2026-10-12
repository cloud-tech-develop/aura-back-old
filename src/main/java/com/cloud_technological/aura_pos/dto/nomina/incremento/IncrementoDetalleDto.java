package com.cloud_technological.aura_pos.dto.nomina.incremento;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Una vinculación del incremento: salario actual → nuevo. */
@Getter
@Setter
public class IncrementoDetalleDto {
    private Long id;
    private Long contratoId;
    private String codigoVinculacion;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String cargo;
    private BigDecimal salarioAnterior;
    private BigDecimal salarioNuevo;
    private BigDecimal diferencia;
    /** Nóminas ya confirmadas desde la fecha de aplicación con el salario anterior. */
    private int nominasRetroactivo;
}
