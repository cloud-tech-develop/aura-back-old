package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.*;

import com.cloud_technological.aura_pos.dto.marcas.CreateMarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.MarcaTableDto;
import com.cloud_technological.aura_pos.dto.marcas.UpdateMarcaDto;
import com.cloud_technological.aura_pos.entity.MarcaEntity;

@Mapper(componentModel = "spring")
public interface MarcaMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    MarcaEntity toEntity(CreateMarcaDto dto);

    MarcaTableDto toTableDto(MarcaEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(UpdateMarcaDto dto, @MappingTarget MarcaEntity entity);
}