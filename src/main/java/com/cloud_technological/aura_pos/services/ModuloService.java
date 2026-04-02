package com.cloud_technological.aura_pos.services;

import java.util.List;
import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.permisos.ModuloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.CreateModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ModuloService {
    ModuloTableDto crear(CreateModuloDto dto);
    ModuloTableDto actualizar(Integer id, UpdateModuloDto dto);
    void eliminar(Integer id);
    ModuloDto obtenerPorId(Integer id);
    PageImpl<ModuloTableDto> page(PageableDto<Object> pageable);
    List<ModuloTableDto> listar();
}
