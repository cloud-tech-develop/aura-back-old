package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.permisos.ModuloPermisoDto;
import com.cloud_technological.aura_pos.dto.permisos.PermisosEmpresaDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdatePermisosDto;

public interface PermisoService {
    PermisosEmpresaDto obtenerPermisosPorEmpresa(Integer empresaId);
    List<ModuloPermisoDto> obtenerModulosPorEmpresa(Integer empresaId);
    PermisosEmpresaDto actualizarPermisos(Integer empresaId, UpdatePermisosDto dto);
    List<ModuloPermisoDto> obtenerPermisosPublicos(String nit);
    boolean tienePermiso(Integer empresaId, String moduloCodigo, String submoduloCodigo);
}
