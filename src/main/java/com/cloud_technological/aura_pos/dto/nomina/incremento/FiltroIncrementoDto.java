package com.cloud_technological.aura_pos.dto.nomina.incremento;

import lombok.Getter;
import lombok.Setter;

/** Filtros del listado de incrementos. */
@Getter
@Setter
public class FiltroIncrementoDto {
    /** PENDIENTE | APLICADO | REVERSADO | ANULADO. */
    private String estado;
}
