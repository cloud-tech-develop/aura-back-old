package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.CreateTipoVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.TipoVinculacionTableDto;
import com.cloud_technological.aura_pos.entity.NominaTipoVinculacionEntity;

/** Entidad ↔ DTO de los tipos de vinculación. */
@Mapper(componentModel = "spring")
public interface NominaTipoVinculacionMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    NominaTipoVinculacionEntity toEntity(CreateTipoVinculacionDto dto);

    @Mapping(target = "totalRows", ignore = true)
    TipoVinculacionTableDto toTableDto(NominaTipoVinculacionEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(CreateTipoVinculacionDto dto, @MappingTarget NominaTipoVinculacionEntity entity);
}
