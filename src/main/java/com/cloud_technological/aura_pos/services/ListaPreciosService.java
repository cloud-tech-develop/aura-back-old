package com.cloud_technological.aura_pos.services;



import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.lista_precios.CreateListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosTableDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateListaPreciosDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ListaPreciosService {
    PageImpl<ListaPreciosTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ListaPreciosDto obtenerPorId(Long id, Integer empresaId);
    ListaPreciosDto crear(CreateListaPreciosDto dto, Integer empresaId);
    ListaPreciosDto actualizar(Long id, UpdateListaPreciosDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
    List<ListaPreciosDto> list(Integer empresaId);
}