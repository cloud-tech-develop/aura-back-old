package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface VentaService {
    PageImpl<VentaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    VentaDto obtenerPorId(Long id, Integer empresaId);
    VentaDto crear(CreateVentaDto dto, Integer empresaId, Long usuarioId);
    void anular(Long id, Integer empresaId);
}
