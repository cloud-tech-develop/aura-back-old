package com.cloud_technological.aura_pos.dto.permisos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePermisosDto {
    private List<ModuloPermisoUpdateDto> modulos;
}
