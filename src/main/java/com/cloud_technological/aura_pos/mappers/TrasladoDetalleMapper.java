package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.traslados.CreateTrasladoDetalleDto;
import com.cloud_technological.aura_pos.dto.traslados.TrasladoDetalleDto;
import com.cloud_technological.aura_pos.entity.TrasladoDetalleEntity;


@Mapper(componentModel = "spring")
public interface TrasladoDetalleMapper {
    
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "traslado", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "lote", ignore = true),
    })
    TrasladoDetalleEntity toEntity(CreateTrasladoDetalleDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "productoSku", source = "entity.producto.sku"),
        @Mapping(target = "loteId", source = "entity.lote.id"),
        @Mapping(target = "codigoLote", source = "entity.lote.codigoLote"),
    })
    TrasladoDetalleDto toDto(TrasladoDetalleEntity entity);
}
