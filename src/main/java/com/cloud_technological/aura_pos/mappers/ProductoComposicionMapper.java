package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.producto_composicion.CreateProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.UpdateProductoComposicionDto;
import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;

@Mapper(componentModel = "spring")
public interface ProductoComposicionMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "productoPadre", ignore = true),
        @Mapping(target = "productoHijo", ignore = true),
    })
    ProductoComposicionEntity toEntity(CreateProductoComposicionDto dto);

    @Mappings({
        @Mapping(target = "productoPadreId", source = "entity.productoPadre.id"),
        @Mapping(target = "productoPadreNombre", source = "entity.productoPadre.nombre"),
        @Mapping(target = "productoHijoId", source = "entity.productoHijo.id"),
        @Mapping(target = "productoHijoNombre", source = "entity.productoHijo.nombre"),
    })
    ProductoComposicionDto toDto(ProductoComposicionEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "productoPadre", ignore = true),
        @Mapping(target = "productoHijo", ignore = true),
    })
    void updateEntityFromDto(UpdateProductoComposicionDto dto, @MappingTarget ProductoComposicionEntity entity);
}