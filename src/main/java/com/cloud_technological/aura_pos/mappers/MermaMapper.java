package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.merma.CreateMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MermaDto;
import com.cloud_technological.aura_pos.entity.MermaEntity;

@Mapper(componentModel = "spring")
public interface MermaMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "sucursal", ignore = true),
        @Mapping(target = "usuario", ignore = true),
        @Mapping(target = "motivo", ignore = true),
        @Mapping(target = "fecha", ignore = true),
        @Mapping(target = "costoTotal", ignore = true),
        @Mapping(target = "estado", ignore = true),
    })
    MermaEntity toEntity(CreateMermaDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "sucursalId", source = "entity.sucursal.id"),
        @Mapping(target = "sucursalNombre", source = "entity.sucursal.nombre"),
        @Mapping(target = "usuarioId", source = "entity.usuario.id"),
        @Mapping(target = "motivoId", source = "entity.motivo.id"),
        @Mapping(target = "motivoNombre", source = "entity.motivo.nombre"),
        @Mapping(target = "detalles", ignore = true),
    })
    MermaDto toDto(MermaEntity entity);
}
