package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.compras.CompraDetalleDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDetalleDto;
import com.cloud_technological.aura_pos.entity.CompraDetalleEntity;

@Mapper(componentModel = "spring")
public interface CompraDetalleMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "compra", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "lote", ignore = true),
        @Mapping(target = "subtotalLinea", ignore = true),
        @Mapping(target = "descuentoValor", ignore = true),
        @Mapping(target = "precioVenta1", ignore = true),
        @Mapping(target = "precioVenta2", ignore = true),
        @Mapping(target = "precioVenta3", ignore = true),
    })
    CompraDetalleEntity toEntity(CreateCompraDetalleDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "productoSku", source = "entity.producto.sku"),
    })
    CompraDetalleDto toDto(CompraDetalleEntity entity);
}
