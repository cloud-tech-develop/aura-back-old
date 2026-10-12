package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.CreateTipoComprobanteDto;
import com.cloud_technological.aura_pos.entity.NominaTipoComprobanteEntity;

/** DTO → entidad de los tipos de comprobante (la lectura la arma el QueryRepository). */
@Mapper(componentModel = "spring")
public interface NominaTipoComprobanteMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    NominaTipoComprobanteEntity toEntity(CreateTipoComprobanteDto dto);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(CreateTipoComprobanteDto dto, @MappingTarget NominaTipoComprobanteEntity entity);
}
