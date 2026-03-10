package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.facturacion.ReciboPagoDto;
import com.cloud_technological.aura_pos.entity.ReciboPagoEntity;


@Mapper(componentModel = "spring")
public interface ReciboPagoMapper {
    
    @Mapping(target = "facturaId", source = "entity.factura.id")
    ReciboPagoDto toDto(ReciboPagoEntity entity);
    
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "factura", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
    })
    ReciboPagoEntity toEntity(ReciboPagoDto dto);
}
