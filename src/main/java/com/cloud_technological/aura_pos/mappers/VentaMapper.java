package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.ventas.VentaDto;
import com.cloud_technological.aura_pos.entity.VentaEntity;


@Mapper(componentModel = "spring")
public interface VentaMapper {
    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "sucursalId", source = "entity.sucursal.id"),
        @Mapping(target = "sucursalNombre", source = "entity.sucursal.nombre"),
        @Mapping(target = "clienteId", source = "entity.cliente.id"),
        @Mapping(target = "clienteNombre", expression = "java(resolverNombreCliente(entity))"),
        @Mapping(target = "usuarioId", source = "entity.usuario.id"),
        @Mapping(target = "turnoCajaId", source = "entity.turnoCaja.id"),
        @Mapping(target = "detalles", ignore = true),
        @Mapping(target = "pagos", ignore = true),
    })
    VentaDto toDto(VentaEntity entity);

    default String resolverNombreCliente(VentaEntity entity) {
        if (entity.getCliente() == null) return "Consumidor Final";
        String razonSocial = entity.getCliente().getRazonSocial();
        if (razonSocial != null && !razonSocial.isBlank()) return razonSocial;
        return entity.getCliente().getNombres() + " " + entity.getCliente().getApellidos();
    }
}
