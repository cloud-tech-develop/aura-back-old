package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.merma.CreateMermaDetalleDto;
import com.cloud_technological.aura_pos.dto.merma.MermaDetalleDto;
import com.cloud_technological.aura_pos.entity.MermaDetalleEntity;


@Mapper(componentModel = "spring")
public interface MermaDetalleMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "merma", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "lote", ignore = true),
    })
    MermaDetalleEntity toEntity(CreateMermaDetalleDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "loteId", source = "entity.lote.id"),
        @Mapping(target = "codigoLote", source = "entity.lote.codigoLote"),
    })
    MermaDetalleDto toDto(MermaDetalleEntity entity);
}
