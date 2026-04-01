package com.cloud_technological.aura_pos.dto.permisos;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ModuloPermisoDto {
    private Integer moduloId;
    private String moduloCodigo;
    private String moduloNombre;
    private Boolean activo;
    private List<SubmoduloPermisoDto> submodulos;
}
