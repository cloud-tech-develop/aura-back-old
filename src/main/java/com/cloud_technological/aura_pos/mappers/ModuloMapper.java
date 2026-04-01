package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.cloud_technological.aura_pos.dto.permisos.ModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.CreateModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateModuloDto;
import com.cloud_technological.aura_pos.entity.ModuloEntity;

@Mapper(componentModel = "spring")
public interface ModuloMapper {
    ModuloDto toDto(ModuloEntity entity);
    ModuloTableDto toTableDto(ModuloEntity entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "submodulos", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ModuloEntity toEntity(CreateModuloDto dto);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "submodulos", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(UpdateModuloDto dto, @MappingTarget ModuloEntity entity);
}
