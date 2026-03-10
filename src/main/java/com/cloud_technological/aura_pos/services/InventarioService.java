package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.inventario.CreateInventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioTableDto;
import com.cloud_technological.aura_pos.dto.inventario.UpdateInventarioDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface InventarioService {
    PageImpl<InventarioTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    InventarioDto obtenerPorId(Long id, Integer empresaId);
    List<InventarioTableDto> listarStockBajo(Integer empresaId);
    InventarioDto crear(CreateInventarioDto dto, Integer empresaId);
    InventarioDto actualizar(Long id, UpdateInventarioDto dto, Integer empresaId);
}
