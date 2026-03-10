package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.merma.CreateMotivoMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MotivoMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MotivoMermaTableDto;
import com.cloud_technological.aura_pos.dto.merma.UpdateMotivoMermaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface MotivoMermaService {
    PageImpl<MotivoMermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    MotivoMermaDto obtenerPorId(Long id, Integer empresaId);
    MotivoMermaDto crear(CreateMotivoMermaDto dto, Integer empresaId);
    MotivoMermaDto actualizar(Long id, UpdateMotivoMermaDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}
