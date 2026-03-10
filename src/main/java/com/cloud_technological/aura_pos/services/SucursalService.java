package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.sucursal.CreateSucursalDto;
import com.cloud_technological.aura_pos.dto.sucursal.SucursalDto;
import com.cloud_technological.aura_pos.dto.sucursal.SucursalTableDto;
import com.cloud_technological.aura_pos.dto.sucursal.UpdateSucursalDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface SucursalService {
    PageImpl<SucursalTableDto> paginar(PageableDto<Object> pageable, Integer empresaId);

    SucursalDto obtenerPorId(Integer id, Integer empresaId);

    List<SucursalDto> listarActivas(Integer empresaId);

    SucursalDto crear(CreateSucursalDto dto, Integer empresaId);

    SucursalDto actualizar(Integer id, UpdateSucursalDto dto, Integer empresaId);

    void eliminar(Integer id, Integer empresaId);
}
