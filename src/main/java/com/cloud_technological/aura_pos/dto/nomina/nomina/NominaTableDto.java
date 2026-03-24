package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NominaTableDto {
    private Long id;
    private Long periodoId;
    private LocalDate periodoFechaInicio;
    private LocalDate periodoFechaFin;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String cargo;
    private Integer diasTrabajados;
    private BigDecimal totalDevengado;
    private BigDecimal totalDeducciones;
    private BigDecimal netoPagar;
    private String estado;
    private long totalRows;
}
