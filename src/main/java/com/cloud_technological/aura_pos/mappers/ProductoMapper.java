package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.productos.CreateProductoDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoDto;
import com.cloud_technological.aura_pos.dto.productos.UpdateProductoDto;
import com.cloud_technological.aura_pos.entity.ProductoEntity;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "categoria", ignore = true),
        @Mapping(target = "marca", ignore = true),
        @Mapping(target = "unidadMedidaBase", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    ProductoEntity toEntity(CreateProductoDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "categoriaId", source = "entity.categoria.id"),
        @Mapping(target = "categoriaNombre", source = "entity.categoria.nombre"),
        @Mapping(target = "marcaId", source = "entity.marca.id"),
        @Mapping(target = "marcaNombre", source = "entity.marca.nombre"),
        @Mapping(target = "unidadMedidaBaseId", source = "entity.unidadMedidaBase.id"),
        @Mapping(target = "unidadMedidaNombre", source = "entity.unidadMedidaBase.nombre"),
    })
    ProductoDto toDto(ProductoEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "categoria", ignore = true),
        @Mapping(target = "marca", ignore = true),
        @Mapping(target = "unidadMedidaBase", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(UpdateProductoDto dto, @MappingTarget ProductoEntity entity);
}