package com.cloud_technological.aura_pos.dto.nomina.vacaciones;

import lombok.Getter;
import lombok.Setter;

/** Filtros del listado de vacaciones. */
@Getter
@Setter
public class FiltroVacacionDto {
    /** REGISTRADA | INTERRUMPIDA | ANULADA. */
    private String estado;
    private Long empleadoId;
}
