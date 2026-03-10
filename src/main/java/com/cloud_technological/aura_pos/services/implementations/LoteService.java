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
    List<LoteTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId);
    LoteDto crear(CreateLoteDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}
