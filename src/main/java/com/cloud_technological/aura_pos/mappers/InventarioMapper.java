package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.inventario.CreateInventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.UpdateInventarioDto;
import com.cloud_technological.aura_pos.entity.InventarioEntity;

@Mapper(componentModel = "spring")
public interface  InventarioMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "stockActual", source = "dto.stockActual"),
        @Mapping(target = "updatedAt", ignore = true),
    })
    InventarioEntity toEntity(CreateInventarioDto dto);

    @Mappings({
        @Mapping(target = "sucursalId", source = "entity.sucursal.id"),
        @Mapping(target = "sucursalNombre", source = "entity.sucursal.nombre"),
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "productoSku", source = "entity.producto.sku"),
    })
    InventarioDto toDto(InventarioEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "stockActual", source = "dto.stockActual"),
        @Mapping(target = "updatedAt", ignore = true),
    })
    void updateEntityFromDto(UpdateInventarioDto dto, @MappingTarget InventarioEntity entity);
}
