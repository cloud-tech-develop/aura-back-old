package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.lista_precios.CreateProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioTableDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateProductoPrecioDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ProductoPrecioService {
    PageImpl<ProductoPrecioTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ProductoPrecioDto obtenerPorId(Long id, Integer empresaId);
    List<ProductoPrecioTableDto> listarPorLista(Long listaPrecioId);
    ProductoPrecioDto crear(CreateProductoPrecioDto dto, Integer empresaId);
    ProductoPrecioDto actualizar(Long id, UpdateProductoPrecioDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}