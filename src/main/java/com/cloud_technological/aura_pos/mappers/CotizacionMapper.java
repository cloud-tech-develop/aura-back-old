package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionDto;
import com.cloud_technological.aura_pos.entity.CotizacionEntity;

@Mapper(componentModel = "spring")
public interface CotizacionMapper {

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "terceroId", source = "entity.tercero.id"),
        @Mapping(target = "terceroNombre", expression = "java(resolverNombreTercero(entity))"),
        @Mapping(target = "terceroDocumento", source = "entity.tercero.numeroDocumento"),
        @Mapping(target = "detalles", ignore = true),
    })
    CotizacionDto toDto(CotizacionEntity entity);

    default String resolverNombreTercero(CotizacionEntity entity) {
        if (entity.getTercero() == null) return "Consumidor Final";
        String razonSocial = entity.getTercero().getRazonSocial();
        if (razonSocial != null && !razonSocial.isBlank()) return razonSocial;
        return entity.getTercero().getNombres() + " " + entity.getTercero().getApellidos();
    }
}
