package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.categorias.CategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.CategoriaTableDto;
import com.cloud_technological.aura_pos.dto.categorias.CreateCategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.UpdateCategoriaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import java.util.List;

public interface CategoriaService {
    PageImpl<CategoriaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    CategoriaTableDto obtenerPorId(Long id, Integer empresaId);
    CategoriaTableDto crear(CreateCategoriaDto dto, Integer empresaId);
    CategoriaTableDto actualizar(Long id, UpdateCategoriaDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
    List<CategoriaDto> list(Integer empresaId);
}
