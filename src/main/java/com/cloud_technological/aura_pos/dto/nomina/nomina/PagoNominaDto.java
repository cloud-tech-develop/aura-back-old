package com.cloud_technological.aura_pos.dto.nomina.nomina;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PagoNominaDto {
    private String medioPago;        // EFECTIVO | TRANSFERENCIA — obligatorio
    private Long cuentaBancariaId;   // requerido si TRANSFERENCIA

    /**
     * Cuenta de fondos elegida a mano (caja menor, fondo de nómina…). Manda
     * sobre la caja: el efectivo sale de esa cuenta y no toca ningún arqueo.
     */
    private Long cuentaContableId;

    /** Caja de la que sale el efectivo; sin ella se usa la única abierta de la sucursal. */
    private Long turnoCajaId;
    private Integer sucursalId;
}
