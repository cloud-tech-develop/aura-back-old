package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.producto_presentacion.CreateProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.UpdateProductoPresentacionDto;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;

@Mapper(componentModel = "spring")
public interface ProductoPresentacionMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "producto", ignore = true),
    })
    ProductoPresentacionEntity toEntity(CreateProductoPresentacionDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
    })
    ProductoPresentacionDto toDto(ProductoPresentacionEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "producto", ignore = true),
    })
    void updateEntityFromDto(UpdateProductoPresentacionDto dto, @MappingTarget ProductoPresentacionEntity entity);
}