package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.producto_composicion.CreateProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.UpdateProductoComposicionDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ProductoComposicionService {
    PageImpl<ProductoComposicionTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ProductoComposicionDto obtenerPorId(Long id, Integer empresaId);
    List<ProductoComposicionTableDto> listarPorPadre(Long productoPadreId);
    ProductoComposicionDto crear(CreateProductoComposicionDto dto, Integer empresaId);
    ProductoComposicionDto actualizar(Long id, UpdateProductoComposicionDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}