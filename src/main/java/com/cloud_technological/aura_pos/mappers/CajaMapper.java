package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.caja.CajaDto;
import com.cloud_technological.aura_pos.dto.caja.CreateCajaDto;
import com.cloud_technological.aura_pos.dto.caja.UpdateCajaDto;
import com.cloud_technological.aura_pos.entity.CajaEntity;


@Mapper(componentModel = "spring")
public interface CajaMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
    })
    CajaEntity toEntity(CreateCajaDto dto);

    @Mappings({
        @Mapping(target = "sucursalId", source = "entity.sucursal.id"),
        @Mapping(target = "sucursalNombre", source = "entity.sucursal.nombre"),
    })
    CajaDto toDto(CajaEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
    })
    void updateEntityFromDto(UpdateCajaDto dto, @MappingTarget CajaEntity entity);
}
