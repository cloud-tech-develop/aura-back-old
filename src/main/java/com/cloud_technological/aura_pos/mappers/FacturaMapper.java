package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.facturacion.FacturaDto;
import com.cloud_technological.aura_pos.entity.FacturaEntity;


@Mapper(componentModel = "spring")
public interface FacturaMapper {
    
    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "usuarioId", source = "entity.usuario.id"),
        @Mapping(target = "ventaId", source = "entity.venta.id"),
    })
    FacturaDto toDto(FacturaEntity entity);
    
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "usuario", ignore = true),
        @Mapping(target = "venta", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    FacturaEntity toEntity(FacturaDto dto);
}
