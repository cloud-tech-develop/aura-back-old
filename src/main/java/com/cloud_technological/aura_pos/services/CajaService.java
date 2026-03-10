package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.caja.CajaDto;
import com.cloud_technological.aura_pos.dto.caja.CajaTableDto;
import com.cloud_technological.aura_pos.dto.caja.CreateCajaDto;
import com.cloud_technological.aura_pos.dto.caja.UpdateCajaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CajaService {
    PageImpl<CajaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    CajaDto obtenerPorId(Long id, Integer empresaId);
    CajaDto crear(CreateCajaDto dto, Integer empresaId);
    CajaDto actualizar(Long id, UpdateCajaDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}
