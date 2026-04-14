package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.compras.CreateGastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface GastoService {
    GastoDto obtener(Long id, Integer empresaId);
    GastoDto crear(CreateGastoDto dto, Integer empresaId, Long usuarioId);
    GastoDto actualizar(Long id, CreateGastoDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
    PageImpl<GastoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
}
