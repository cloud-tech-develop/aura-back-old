package com.cloud_technological.aura_pos.dto.nomina.retiro;

import lombok.Getter;
import lombok.Setter;

/** Filtros del listado de retiros. */
@Getter
@Setter
public class FiltroRetiroDto {
    /** PENDIENTE | EN_LIQUIDACION | LIQUIDADO | PAGADO | ANULADO. */
    private String estado;
}
