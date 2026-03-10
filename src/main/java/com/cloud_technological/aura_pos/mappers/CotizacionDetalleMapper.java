package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.cotizaciones.CreateCotizacionDetalleDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionDetalleDto;
import com.cloud_technological.aura_pos.entity.CotizacionDetalleEntity;

@Mapper(componentModel = "spring")
public interface CotizacionDetalleMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "cotizacion", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "subtotal", ignore = true),
    })
    CotizacionDetalleEntity toEntity(CreateCotizacionDetalleDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "productoSku", source = "entity.producto.sku"),
    })
    CotizacionDetalleDto toDto(CotizacionDetalleEntity entity);
}
