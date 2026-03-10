package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.lista_precios.CreateProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateProductoPrecioDto;
import com.cloud_technological.aura_pos.entity.ProductoPrecioEntity;

@Mapper(componentModel = "spring")
public interface ProductoPrecioMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "listaPrecio", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
    })
    ProductoPrecioEntity toEntity(CreateProductoPrecioDto dto);

    @Mappings({
        @Mapping(target = "listaPrecioId", source = "entity.listaPrecio.id"),
        @Mapping(target = "listaPrecioNombre", source = "entity.listaPrecio.nombre"),
        @Mapping(target = "productoPresentacionId", source = "entity.productoPresentacion.id"),
        @Mapping(target = "productoPresentacionNombre", source = "entity.productoPresentacion.nombre"),
        @Mapping(target = "productoNombre", source = "entity.productoPresentacion.producto.nombre"),
    })
    ProductoPrecioDto toDto(ProductoPrecioEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "listaPrecio", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
    })
    void updateEntityFromDto(UpdateProductoPrecioDto dto, @MappingTarget ProductoPrecioEntity entity);
}