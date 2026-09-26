package com.cloud_technological.aura_pos.dto.bodegas;

import lombok.Getter;
import lombok.Setter;

/** Para selects: lo mínimo que necesita un combo de bodega. */
@Getter
@Setter
public class BodegaDto {
    private Long id;
    private String codigo;
    private String nombre;
    private Integer sucursalId;
    private String sucursalNombre;
    private Boolean esPrincipal;
    private Boolean permiteVenta;
}
