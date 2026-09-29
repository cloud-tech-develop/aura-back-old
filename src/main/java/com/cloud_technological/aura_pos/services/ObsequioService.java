package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.obsequio.CreateObsequioDto;
import com.cloud_technological.aura_pos.dto.obsequio.ObsequioDto;
import com.cloud_technological.aura_pos.dto.obsequio.ObsequioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ObsequioService {

    PageImpl<ObsequioTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    ObsequioDto obtenerPorId(Long id, Integer empresaId);

    ObsequioDto crear(CreateObsequioDto dto, Integer empresaId, Long usuarioId);

    void anular(Long id, Integer empresaId);
}
