package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.producto_presentacion.CreateProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionTableDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.UpdateProductoPresentacionDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ProductoPresentacionService {
    PageImpl<ProductoPresentacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    ProductoPresentacionDto obtenerPorId(Long id, Integer empresaId);
    List<ProductoPresentacionTableDto> listarPorProducto(Long productoId);
    ProductoPresentacionDto crear(CreateProductoPresentacionDto dto, Integer empresaId);
    ProductoPresentacionDto actualizar(Long id, UpdateProductoPresentacionDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}
