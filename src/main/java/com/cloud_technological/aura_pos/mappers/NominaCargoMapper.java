package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.nomina.cargos.CreateNominaCargoDto;
import com.cloud_technological.aura_pos.entity.NominaCargoEntity;

/** DTO → entidad de cargos. La lectura sale del QueryRepository (con nombres de nivel y tipo). */
@Mapper(componentModel = "spring")
public interface NominaCargoMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    NominaCargoEntity toEntity(CreateNominaCargoDto dto);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(CreateNominaCargoDto dto, @MappingTarget NominaCargoEntity entity);
}
