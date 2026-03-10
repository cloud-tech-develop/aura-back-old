package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.cloud_technological.aura_pos.dto.categorias.CategoriaTableDto;
import com.cloud_technological.aura_pos.dto.categorias.CreateCategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.UpdateCategoriaDto;
import com.cloud_technological.aura_pos.entity.CategoriaEntity;

@Mapper(componentModel = "spring")
public interface CategoriaMapper {
    // Entity -> TableDto (Mapeamos el nombre del padre manualmente)
    @Mapping(target = "nombrePadre", source = "padre.nombre")
    CategoriaTableDto toTableDto(CategoriaEntity entity);

    // CreateDto -> Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "empresa", ignore = true)
    @Mapping(target = "padre", ignore = true) // Lo seteamos en el servicio
    @Mapping(target = "activo", constant = "true")
    CategoriaEntity toEntity(CreateCategoriaDto dto);

    // UpdateDto -> Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "empresa", ignore = true)
    @Mapping(target = "padre", ignore = true) // Lo manejamos en el servicio para evitar ciclos
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(UpdateCategoriaDto dto, @MappingTarget CategoriaEntity entity);
    
}
