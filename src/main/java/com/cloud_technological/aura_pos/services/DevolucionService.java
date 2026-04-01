package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.devolucion.CreateDevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface DevolucionService {
    PageImpl<DevolucionTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    DevolucionDto obtenerPorId(Long id, Integer empresaId);
    DevolucionDto crear(CreateDevolucionDto dto, Integer empresaId, Long usuarioId);
    void anular(Long id, Integer empresaId);
}
