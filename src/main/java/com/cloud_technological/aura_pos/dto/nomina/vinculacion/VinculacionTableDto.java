package com.cloud_technological.aura_pos.dto.nomina.vinculacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Fila del listado de vinculaciones. */
@Getter
@Setter
public class VinculacionTableDto {
    private Long id;
    private String codigo;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String tipoVinculacionNombre;
    private String cargoNombre;
    private String grupoNominaNombre;
    private String tipoContrato;
    private String tipoRemuneracion;
    private BigDecimal salarioBase;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDate fechaFinPrueba;
    private Boolean esPrincipal;
    /** ACTIVO | SUSPENDIDO | TERMINADO. */
    private String estado;
    private long totalRows;
}
