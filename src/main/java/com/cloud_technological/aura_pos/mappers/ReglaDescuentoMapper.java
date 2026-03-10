package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.reglas_descuento.CreateReglaDescuentoDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.UpdateReglaDescuentoDto;
import com.cloud_technological.aura_pos.entity.ReglaDescuentoEntity;

@Mapper(componentModel = "spring")
public interface ReglaDescuentoMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "categoria", ignore = true),
        @Mapping(target = "producto", ignore = true),
    })
    ReglaDescuentoEntity toEntity(CreateReglaDescuentoDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "categoriaId", source = "entity.categoria.id"),
        @Mapping(target = "categoriaNombre", source = "entity.categoria.nombre"),
        @Mapping(target = "productoId", source = "entity.producto.id"),
        @Mapping(target = "productoNombre", source = "entity.producto.nombre"),
    })
    ReglaDescuentoDto toDto(ReglaDescuentoEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "categoria", ignore = true),
        @Mapping(target = "producto", ignore = true),
    })
    void updateEntityFromDto(UpdateReglaDescuentoDto dto, @MappingTarget ReglaDescuentoEntity entity);
}