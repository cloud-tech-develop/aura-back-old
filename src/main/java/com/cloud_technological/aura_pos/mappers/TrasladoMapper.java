package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.traslados.CreateTrasladoDto;
import com.cloud_technological.aura_pos.dto.traslados.TrasladoDto;
import com.cloud_technological.aura_pos.entity.TrasladoEntity;


@Mapper(componentModel = "spring")
public interface TrasladoMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "sucursalOrigen", ignore = true),
        @Mapping(target = "sucursalDestino", ignore = true),
        @Mapping(target = "usuario", ignore = true),
        @Mapping(target = "fecha", ignore = true),
        @Mapping(target = "estado", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
    })
    TrasladoEntity toEntity(CreateTrasladoDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "sucursalOrigenId", source = "entity.sucursalOrigen.id"),
        @Mapping(target = "sucursalOrigenNombre", source = "entity.sucursalOrigen.nombre"),
        @Mapping(target = "sucursalDestinoId", source = "entity.sucursalDestino.id"),
        @Mapping(target = "sucursalDestinoNombre", source = "entity.sucursalDestino.nombre"),
        @Mapping(target = "usuarioId", source = "entity.usuario.id"),
        @Mapping(target = "detalles", ignore = true),
    })
    TrasladoDto toDto(TrasladoEntity entity);
}
