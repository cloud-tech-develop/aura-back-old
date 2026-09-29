package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * "Documento" de nómina de un período: cabecera con totales + empleados liquidados
 * + novedades aplicadas. Alimenta la vista maestro-detalle del front.
 */
@Getter
@Setter
public class PeriodoResumenDto {
    private Long periodoId;
    private String documento;          // etiqueta de documento, ej. NOM-12
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;             // estado del período

    private Integer cantidadEmpleados;
    private BigDecimal totalDevengado;
    private BigDecimal totalDeducciones;
    private BigDecimal totalNeto;

    private List<NominaTableDto> empleados = new ArrayList<>();
    private List<NovedadResumenDto> novedades = new ArrayList<>();
}
