package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.marcas.MarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.CreateMarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.MarcaTableDto;
import com.cloud_technological.aura_pos.dto.marcas.UpdateMarcaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface MarcaService {
    PageImpl<MarcaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    MarcaTableDto obtenerPorId(Long id, Integer empresaId);
    MarcaTableDto crear(CreateMarcaDto dto, Integer empresaId);
    MarcaTableDto actualizar(Long id, UpdateMarcaDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
    List<MarcaDto> list(Integer empresaId);
}