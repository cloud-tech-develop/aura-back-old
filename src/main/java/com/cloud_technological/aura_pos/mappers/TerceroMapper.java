package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.terceros.CreateTerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.UpdateTerceroDto;
import com.cloud_technological.aura_pos.entity.TerceroEntity;

@Mapper(componentModel = "spring")
public interface TerceroMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "created_at", ignore = true),
        @Mapping(target = "updated_at", ignore = true),
        @Mapping(target = "deleted_at", ignore = true),
        @Mapping(target = "municipioId", source = "dto.municipioId"),
    })
    TerceroEntity toEntity(CreateTerceroDto dto);

    @Mapping(target = "empresaId", source = "entity.empresa.id")
    TerceroDto toDto(TerceroEntity entity);

    @Mappings({
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "created_at", ignore = true),
        @Mapping(target = "updated_at", ignore = true),
        @Mapping(target = "deleted_at", ignore = true),
        @Mapping(target = "municipioId", source = "dto.municipioId"),
    })
    void updateEntityFromDto(UpdateTerceroDto dto, @MappingTarget TerceroEntity entity);
}
