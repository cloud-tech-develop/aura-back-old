package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Proyección del asiento de un documento de nómina (pestaña Contabilidad). */
@Getter
@Setter
public class ProyeccionContableDto {
    private List<LineaProyeccionDto> lineas = new ArrayList<>();
    private BigDecimal totalDebito = BigDecimal.ZERO;
    private BigDecimal totalCredito = BigDecimal.ZERO;
    /** Empleados liquidados que entraron a la proyección. */
    private int empleados;
    /** Problemas que impiden armar el asiento de algún empleado (cuenta sin configurar…). */
    private List<String> advertencias = new ArrayList<>();
}
