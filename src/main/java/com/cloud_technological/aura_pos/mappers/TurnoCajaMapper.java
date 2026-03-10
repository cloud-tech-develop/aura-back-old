package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.caja.TurnoCajaDto;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;

@Mapper(componentModel = "spring")
public interface TurnoCajaMapper {
 @Mappings({
        @Mapping(target = "cajaNombre", source = "entity.caja.nombre"),
        @Mapping(target = "cajaId", source = "entity.caja.id"),
        @Mapping(target = "usuarioId", source = "entity.usuario.id"),
        @Mapping(target = "usuarioNombre", source = "entity.usuario.username"),
    })
    TurnoCajaDto toDto(TurnoCajaEntity entity);
}
