package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.reglas_descuento.CreateReglaDescuentoDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoTableDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.UpdateReglaDescuentoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ReglaDescuentoService {
    PageImpl<ReglaDescuentoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ReglaDescuentoDto obtenerPorId(Long id, Integer empresaId);
    ReglaDescuentoDto crear(CreateReglaDescuentoDto dto, Integer empresaId);
    ReglaDescuentoDto actualizar(Long id, UpdateReglaDescuentoDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}