package com.cloud_technological.aura_pos.dto.nomina.nomina;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PagoNominaDto {
    private String medioPago;        // EFECTIVO | TRANSFERENCIA
    private Long cuentaBancariaId;   // requerido si TRANSFERENCIA
}
