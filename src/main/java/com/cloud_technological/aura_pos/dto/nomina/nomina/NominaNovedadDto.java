package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NominaNovedadDto {
    private Long id;
    private String tipo;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private Boolean esDeduccion;

    // F0 — ausencias con días
    private Integer dias;
    private Boolean afectaDiasSalario;
    private String subtipo;
    private java.time.LocalDate fechaInicio;
    private java.time.LocalDate fechaFin;
}
