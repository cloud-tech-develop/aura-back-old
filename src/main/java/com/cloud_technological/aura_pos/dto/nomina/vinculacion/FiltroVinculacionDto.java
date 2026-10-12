package com.cloud_technological.aura_pos.dto.nomina.vinculacion;

import lombok.Getter;
import lombok.Setter;

/** Filtros del listado de vinculaciones (van en {@code PageableDto.params}). */
@Getter
@Setter
public class FiltroVinculacionDto {
    /** ACTIVO | SUSPENDIDO | TERMINADO; null = todas. */
    private String estado;
    private Long tipoVinculacionId;
    private Long cargoId;
    private Long grupoNominaId;
}
