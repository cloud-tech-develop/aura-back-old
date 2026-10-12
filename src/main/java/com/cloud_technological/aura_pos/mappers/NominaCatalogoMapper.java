package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.nomina.catalogos.CreateNominaCatalogoDto;
import com.cloud_technological.aura_pos.dto.nomina.catalogos.NominaCatalogoTableDto;
import com.cloud_technological.aura_pos.entity.NominaCatalogoEntity;

/**
 * Entidad ↔ DTO de los catálogos de nómina. Los atributos (JSON ↔ mapa) los
 * convierte {@code AtributosCatalogoValidator} en el servicio, porque dependen
 * del tipo de catálogo.
 */
@Mapper(componentModel = "spring")
public interface NominaCatalogoMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "tipo", ignore = true),
        @Mapping(target = "atributos", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    NominaCatalogoEntity toEntity(CreateNominaCatalogoDto dto);

    @Mappings({
        @Mapping(target = "atributos", ignore = true),
        @Mapping(target = "totalRows", ignore = true),
    })
    NominaCatalogoTableDto toTableDto(NominaCatalogoEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "tipo", ignore = true),
        @Mapping(target = "atributos", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(CreateNominaCatalogoDto dto, @MappingTarget NominaCatalogoEntity entity);
}
