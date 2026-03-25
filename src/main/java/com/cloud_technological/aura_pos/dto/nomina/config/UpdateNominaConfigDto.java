package com.cloud_technological.aura_pos.dto.nomina.config;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateNominaConfigDto {
    private String modoNomina;      // COMPLETO | SIMPLIFICADO
    private String periodicidad;    // MENSUAL | QUINCENAL | SEMANAL
    private BigDecimal smmlv;
    private BigDecimal auxilioTransporte;
    private BigDecimal pctSaludEmpleado;
    private BigDecimal pctPensionEmpleado;
    private BigDecimal pctSaludEmpleador;
    private BigDecimal pctPensionEmpleador;
    private BigDecimal pctCajaCompensacion;
    private BigDecimal pctIcbf;
    private BigDecimal pctSena;
}
