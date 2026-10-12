package com.cloud_technological.aura_pos.dto.nomina.contabilidad;

import javax.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** Cambio de la cuenta o el tercero de un concepto de nómina; lo nulo vuelve al valor por defecto. */
@Getter
@Setter
public class GuardarConceptoContableDto {

    private Long cuentaDebitoId;

    private Long cuentaCreditoId;

    @Pattern(regexp = "EMPLEADO|EPS|AFP|ARL|CCF|DIAN|NINGUNO|FIJO", message = "Tercero no válido")
    private String terceroCredito;

    /** Obligatorio cuando {@code terceroCredito} es FIJO. */
    private Long terceroFijoId;
}
