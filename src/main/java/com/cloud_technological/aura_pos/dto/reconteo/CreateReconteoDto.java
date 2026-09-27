package com.cloud_technological.aura_pos.dto.reconteo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReconteoDto {
    private Integer sucursalId;

    /** Bodega que se cuenta. Sin ella, la principal de la sucursal. */
    private Long bodegaId;
    private String tipo; // TOTAL, PARCIAL
    private String observaciones;
}
