package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.traslados.CreateTrasladoDto;
import com.cloud_technological.aura_pos.dto.traslados.TrasladoDto;
import com.cloud_technological.aura_pos.dto.traslados.TrasladoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface TrasladoService {
    PageImpl<TrasladoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    TrasladoDto obtenerPorId(Long id, Integer empresaId);
    TrasladoDto crear(CreateTrasladoDto dto, Integer empresaId, Long usuarioId);
    void anular(Long id, Integer empresaId);
}
