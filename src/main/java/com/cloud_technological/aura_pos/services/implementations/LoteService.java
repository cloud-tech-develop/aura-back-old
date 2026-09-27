package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.inventario.CreateLoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface  LoteService {
    PageImpl<LoteTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    LoteDto obtenerPorId(Long id, Integer empresaId);
    List<LoteTableDto> listarPorVencer(Integer empresaId);
    List<com.cloud_technological.aura_pos.dto.inventario.VencimientoLoteDto> vencimientos(Integer empresaId, Long sucursalId, Integer dias);
    List<LoteTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId, Integer empresaId);
    LoteDto crear(CreateLoteDto dto, Integer empresaId);
    LoteDto actualizar(Long id, com.cloud_technological.aura_pos.dto.inventario.UpdateLoteDto dto,
            Integer empresaId, Long usuarioId);
    void eliminar(Long id, Integer empresaId);
    com.cloud_technological.aura_pos.dto.inventario.ReglasLoteDto obtenerReglas(Integer empresaId);
    com.cloud_technological.aura_pos.dto.inventario.ReglasLoteDto guardarReglas(
            com.cloud_technological.aura_pos.dto.inventario.ReglasLoteDto dto, Integer empresaId);
}
