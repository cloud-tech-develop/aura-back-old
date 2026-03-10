package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.inventario.CreateSerialProductoDto;
import com.cloud_technological.aura_pos.dto.inventario.SerialProductoDto;
import com.cloud_technological.aura_pos.entity.SerialProductoEntity;

@Mapper(componentModel = "spring")
public interface SerialProductoMapper {
    
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
    })
    SerialProductoEntity toEntity(CreateSerialProductoDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "sucursalId", source = "entity.sucursal.id"),
        @Mapping(target = "sucursalNombre", source = "entity.sucursal.nombre"),
    })
    SerialProductoDto toDto(SerialProductoEntity entity);
}
