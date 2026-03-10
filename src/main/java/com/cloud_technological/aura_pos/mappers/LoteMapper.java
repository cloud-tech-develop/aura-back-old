package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.inventario.CreateLoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteDto;
import com.cloud_technological.aura_pos.entity.LoteEntity;

@Mapper(componentModel = "spring")
public interface LoteMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
    })
    LoteEntity toEntity(CreateLoteDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "sucursalId", source = "entity.sucursal.id"),
        @Mapping(target = "sucursalNombre", source = "entity.sucursal.nombre"),
    })
    LoteDto toDto(LoteEntity entity);
}
