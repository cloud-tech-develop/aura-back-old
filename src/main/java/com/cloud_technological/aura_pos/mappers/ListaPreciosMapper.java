package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.lista_precios.CreateListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateListaPreciosDto;
import com.cloud_technological.aura_pos.entity.ListaPreciosEntity;

@Mapper(componentModel = "spring")
public interface ListaPreciosMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
    })
    ListaPreciosEntity toEntity(CreateListaPreciosDto dto);

    @Mapping(target = "empresaId", source = "entity.empresa.id")
    ListaPreciosDto toDto(ListaPreciosEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
    })
    void updateEntityFromDto(UpdateListaPreciosDto dto, @MappingTarget ListaPreciosEntity entity);
}