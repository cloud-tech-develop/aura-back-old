package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.unidad_medida.CreateUnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UpdateUnidadMedidaDto;
import com.cloud_technological.aura_pos.entity.UnidadMedidaEntity;

@Mapper(componentModel = "spring")
public interface UnidadMedidaMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
    })
    UnidadMedidaEntity toEntity(CreateUnidadMedidaDto dto);

    UnidadMedidaDto toDto(UnidadMedidaEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
    })
    void updateEntityFromDto(UpdateUnidadMedidaDto dto, @MappingTarget UnidadMedidaEntity entity);
}