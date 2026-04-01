package com.cloud_technological.aura_pos.dto.permisos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModuloPermisoUpdateDto {
    private Integer moduloId;
    private Boolean activo;
    private List<SubmoduloPermisoUpdateDto> submodulos;
}
