package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.compras.CompraDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDto;
import com.cloud_technological.aura_pos.entity.CompraEntity;

@Mapper(componentModel = "spring")
public interface CompraMapper {
    
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
        @Mapping(target = "proveedor", ignore = true),
        @Mapping(target = "usuario", ignore = true),
        @Mapping(target = "subtotal", ignore = true),
        @Mapping(target = "descuentoTotal", ignore = true),
        @Mapping(target = "impuestosTotal", ignore = true),
        @Mapping(target = "total", ignore = true),
        @Mapping(target = "estado", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "retefuentePct", ignore = true),
        @Mapping(target = "retefuenteValor", ignore = true),
        @Mapping(target = "reteivaPct", ignore = true),
        @Mapping(target = "reteivaValor", ignore = true),
        @Mapping(target = "reteicaPct", ignore = true),
        @Mapping(target = "reteicaValor", ignore = true),
        @Mapping(target = "totalRetenciones", ignore = true),
        @Mapping(target = "netaAPagar", ignore = true),
        @Mapping(target = "formaPago", ignore = true),
    })
    CompraEntity toEntity(CreateCompraDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "sucursalId", source = "entity.sucursal.id"),
        @Mapping(target = "sucursalNombre", source = "entity.sucursal.nombre"),
        @Mapping(target = "proveedorId", source = "entity.proveedor.id"),
        @Mapping(target = "proveedorNombre", expression = "java(resolverNombreProveedor(entity))"),
        @Mapping(target = "usuarioId", source = "entity.usuario.id"),
        @Mapping(target = "detalles", ignore = true),
    })
    CompraDto toDto(CompraEntity entity);

    default String resolverNombreProveedor(CompraEntity entity) {
        if (entity.getProveedor() == null) return "";
        String razonSocial = entity.getProveedor().getRazonSocial();
        if (razonSocial != null && !razonSocial.isBlank()) return razonSocial;
        return entity.getProveedor().getNombres() + " " + entity.getProveedor().getApellidos();
    }
}
