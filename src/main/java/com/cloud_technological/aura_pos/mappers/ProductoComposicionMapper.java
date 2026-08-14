package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.producto_composicion.CreateProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.UpdateProductoComposicionDto;
import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;

/**
 * El mapper solo mueve campos planos. Las relaciones y, sobre todo, `cantidad`
 * —que es derivada— las resuelve ProductoComposicionServiceImpl: mapearlas aquí
 * escondería la fórmula del rendimiento.
 */
@Mapper(componentModel = "spring")
public interface ProductoComposicionMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "productoPadre", ignore = true),
        @Mapping(target = "productoHijo", ignore = true),
        @Mapping(target = "unidadMedida", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
        @Mapping(target = "factorUnidad", ignore = true),
        @Mapping(target = "cantidad", ignore = true),
        @Mapping(target = "cantidadReceta", source = "cantidad"),
    })
    ProductoComposicionEntity toEntity(CreateProductoComposicionDto dto);

    @Mappings({
        @Mapping(target = "productoPadreId", source = "entity.productoPadre.id"),
        @Mapping(target = "productoPadreNombre", source = "entity.productoPadre.nombre"),
        @Mapping(target = "productoHijoId", source = "entity.productoHijo.id"),
        @Mapping(target = "productoHijoNombre", source = "entity.productoHijo.nombre"),
        @Mapping(target = "unidadMedidaId", source = "entity.unidadMedida.id"),
        @Mapping(target = "unidadMedidaAbreviatura", source = "entity.unidadMedida.abreviatura"),
        @Mapping(target = "productoPresentacionId", source = "entity.productoPresentacion.id"),
    })
    ProductoComposicionDto toDto(ProductoComposicionEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "productoPadre", ignore = true),
        @Mapping(target = "productoHijo", ignore = true),
        @Mapping(target = "unidadMedida", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
        @Mapping(target = "factorUnidad", ignore = true),
        @Mapping(target = "cantidad", ignore = true),
        @Mapping(target = "cantidadReceta", source = "cantidad"),
    })
    void updateEntityFromDto(UpdateProductoComposicionDto dto, @MappingTarget ProductoComposicionEntity entity);
}
