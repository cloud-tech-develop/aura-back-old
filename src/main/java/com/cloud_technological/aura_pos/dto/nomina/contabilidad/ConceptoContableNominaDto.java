package com.cloud_technological.aura_pos.dto.nomina.contabilidad;

import lombok.Getter;
import lombok.Setter;

/**
 * Cómo se contabiliza un concepto de nómina: el valor por defecto y, si existe, el
 * cambio de la empresa. Las cuentas van como "código — nombre".
 */
@Getter
@Setter
public class ConceptoContableNominaDto {
    private String codigo;
    private String nombre;
    /** DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION. */
    private String clase;

    /** ¿El concepto tiene débito propio? (devengados, aportes y provisiones). */
    private boolean aplicaDebito;
    /** ¿El concepto tiene crédito propio? (deducciones, aportes y provisiones; el devengado va al neto). */
    private boolean aplicaCredito;

    private String debitoDefecto;
    private String creditoDefecto;
    private String terceroDefecto;

    private Long cuentaDebitoId;
    private String cuentaDebito;
    private Long cuentaCreditoId;
    private String cuentaCredito;
    private String terceroCredito;
    private Long terceroFijoId;
    private String terceroFijo;

    /** ¿La empresa cambió algo de este concepto? */
    private boolean personalizado;
}
