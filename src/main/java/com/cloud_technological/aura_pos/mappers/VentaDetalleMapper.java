package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaDetalleDto;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;


@Mapper(componentModel = "spring")
public interface VentaDetalleMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "venta", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
        @Mapping(target = "lote", ignore = true),
        @Mapping(target = "reglaDescuento", ignore = true),
        @Mapping(target = "impuestoValor", ignore = true),
        @Mapping(target = "subtotalLinea", ignore = true),
    })
    VentaDetalleEntity toEntity(CreateVentaDetalleDto dto);

    @Mappings({
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
        @Mapping(target = "productoSku", source = "entity.producto.sku"),
        @Mapping(target = "productoPresentacionId", source = "entity.productoPresentacion.id"),
        @Mapping(target = "presentacionNombre", source = "entity.productoPresentacion.nombre"),
        @Mapping(target = "loteId", source = "entity.lote.id"),
        @Mapping(target = "codigoLote", source = "entity.lote.codigoLote"),
        @Mapping(target = "unidadMedidaNombre", source = "entity.producto.unidadMedidaBase.nombre"),
    })
    VentaDetalleDto toDto(VentaDetalleEntity entity);
}
