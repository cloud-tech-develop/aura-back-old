package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.inventario.CreateSerialProductoDto;
import com.cloud_technological.aura_pos.dto.inventario.SerialProductoDto;
import com.cloud_technological.aura_pos.dto.inventario.SerialProductoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface SerialProductoService {
    PageImpl<SerialProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    SerialProductoDto obtenerPorId(Long id, Integer empresaId);
    List<com.cloud_technological.aura_pos.dto.inventario.SerialBuscadoDto> buscarDisponible(String codigo, Long sucursalId, Integer empresaId);

    List<SerialProductoTableDto> vendidosEnLinea(Long ventaDetalleId, Integer empresaId);

    List<com.cloud_technological.aura_pos.dto.inventario.SerialTrazaDto> trazabilidad(String serial, Integer empresaId);

    List<SerialProductoTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId, Integer empresaId);
    SerialProductoDto crear(CreateSerialProductoDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}
