package com.cloud_technological.aura_pos.dto.permisos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SubmoduloPermisoDto {
    private Integer submoduloId;
    private String submoduloCodigo;
    private String submoduloNombre;
    private Boolean activo;
}
