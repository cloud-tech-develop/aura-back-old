package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.reconteo.CreateReconteoDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoResponseDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoTableDto;
import com.cloud_technological.aura_pos.dto.reconteo.UpdateReconteoDetalleDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ReconteoService {
    PageImpl<ReconteoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ReconteoResponseDto obtenerPorId(Long id, Integer empresaId);
    ReconteoResponseDto crear(CreateReconteoDto dto, Integer empresaId, Long usuarioId);
    ReconteoResponseDto actualizarDetalle(Long reconteoId, Long detalleId, UpdateReconteoDetalleDto dto, Integer empresaId);
    ReconteoResponseDto aprobar(Long id, Integer empresaId, Long usuarioId);
    ReconteoResponseDto anular(Long id, Integer empresaId);
}
