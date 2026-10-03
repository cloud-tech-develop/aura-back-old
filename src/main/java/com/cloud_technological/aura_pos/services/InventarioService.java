package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.inventario.CreateInventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.HistorialProductoResponseDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioTableDto;
import com.cloud_technological.aura_pos.dto.inventario.UpdateInventarioDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface InventarioService {
    PageImpl<InventarioTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    InventarioDto obtenerPorId(Long id, Integer empresaId);
    List<InventarioTableDto> listarStockBajo(Integer empresaId);

    /** Saldos en o bajo su punto de reorden, con lo que conviene pedir (V185). */
    List<com.cloud_technological.aura_pos.dto.inventario.SugeridoCompraDto> sugeridoCompra(
            Integer empresaId, Long sucursalId, Long bodegaId);
    InventarioDto crear(CreateInventarioDto dto, Integer empresaId);
    InventarioDto actualizar(Long id, UpdateInventarioDto dto, Integer empresaId);
    HistorialProductoResponseDto historialProducto(Long productoId, Long sucursalId, Integer empresaId);
}
