package com.cloud_technological.aura_pos.dto.caja;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CajaDto {
    private Long id;
    private Long sucursalId;
    private String sucursalNombre;
    private String nombre;
    private Boolean activa;
}
