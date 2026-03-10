package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.compras.CreateMovimientoDto;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MovimientoInventarioMapper {
    
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
        @Mapping(target = "producto", ignore = true),
        @Mapping(target = "lote", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
    })
    MovimientoInventarioEntity toEntity(CreateMovimientoDto dto);
}
