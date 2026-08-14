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

    // Los campos de V97 (nombre1/2, apellido1/2, fechaNacimiento, sexo,
    // representante legal, autorretenedores, bancarios) se mapean solos:
    // MapStruct empareja por nombre y coinciden en DTO y entidad.

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "created_at", ignore = true),
        @Mapping(target = "updated_at", ignore = true),
        @Mapping(target = "deleted_at", ignore = true),
        @Mapping(target = "municipioId", source = "dto.municipioId"),
        // `banco` (VARCHAR) está deprecado: no se acepta al crear.
        // La entidad financiera se indica con bancoTerceroId (FK).
        @Mapping(target = "banco", ignore = true),
    })
    TerceroEntity toEntity(CreateTerceroDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        // `roles` no vive en la entidad: se llena aparte desde tercero_rol.
        // Ver TerceroServiceImpl.obtenerPorId().
        @Mapping(target = "roles", ignore = true),
    })
    TerceroDto toDto(TerceroEntity entity);

    @Mappings({
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "created_at", ignore = true),
        @Mapping(target = "updated_at", ignore = true),
        @Mapping(target = "deleted_at", ignore = true),
        @Mapping(target = "municipioId", source = "dto.municipioId"),
        @Mapping(target = "banco", ignore = true),
    })
    void updateEntityFromDto(UpdateTerceroDto dto, @MappingTarget TerceroEntity entity);
}
