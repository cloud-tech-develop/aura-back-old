package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.compras.CompraDto;
import com.cloud_technological.aura_pos.dto.compras.CompraTableDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CompraService {
    PageImpl<CompraTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    CompraDto obtenerPorId(Long id, Integer empresaId);
    CompraDto crear(CreateCompraDto dto, Integer empresaId, Long usuarioId);
    void anular(Long id, Integer empresaId);
    CompraDto actualizar(Long id, CreateCompraDto dto, Integer empresaId, Long usuarioId);
}
