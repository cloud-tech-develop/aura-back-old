package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.merma.CreateMotivoMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MotivoMermaDto;
import com.cloud_technological.aura_pos.dto.merma.UpdateMotivoMermaDto;
import com.cloud_technological.aura_pos.entity.MotivoMermaEntity;


@Mapper(componentModel = "spring")
public interface MotivoMermaMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
    })
    MotivoMermaEntity toEntity(CreateMotivoMermaDto dto);

    @Mapping(target = "empresaId", source = "entity.empresa.id")
    MotivoMermaDto toDto(MotivoMermaEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
    })
    void updateEntityFromDto(UpdateMotivoMermaDto dto, @MappingTarget MotivoMermaEntity entity);
}
