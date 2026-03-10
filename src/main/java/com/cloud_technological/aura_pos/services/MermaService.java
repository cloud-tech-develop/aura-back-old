package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.merma.CreateMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MermaDto;
import com.cloud_technological.aura_pos.dto.merma.MermaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface MermaService {
    PageImpl<MermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    MermaDto obtenerPorId(Long id, Integer empresaId);
    MermaDto crear(CreateMermaDto dto, Integer empresaId, Long usuarioId);
    void anular(Long id, Integer empresaId);
}
