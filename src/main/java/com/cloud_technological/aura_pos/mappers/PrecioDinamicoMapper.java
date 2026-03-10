package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.DescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioVolumenDto;
import com.cloud_technological.aura_pos.entity.DescuentoClienteEntity;
import com.cloud_technological.aura_pos.entity.PrecioClienteEntity;
import com.cloud_technological.aura_pos.entity.PrecioVolumenEntity;

@Mapper(componentModel = "spring")
public interface PrecioDinamicoMapper {

    // ========== PRECIO CLIENTE ==========

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
    })
    PrecioClienteEntity toEntity(CreatePrecioClienteDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "terceroId", source = "entity.tercero.id"),   
        @Mapping(target = "terceroNombre", source = "entity.tercero.nombres"),
        @Mapping(target = "terceroDocumento", source = "entity.tercero.numeroDocumento"),
        @Mapping(target = "productoPresentacionId", source = "entity.productoPresentacion.id"),
        @Mapping(target = "productoPresentacionNombre", source = "entity.productoPresentacion.nombre"),
        @Mapping(target = "productoNombre", source = "entity.productoPresentacion.producto.nombre"),
    })
    PrecioClienteDto toDto(PrecioClienteEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
    })
    void updateEntityFromDto(UpdatePrecioClienteDto dto, @MappingTarget PrecioClienteEntity entity);

    // ========== DESCUENTO CLIENTE ==========

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "categoria", ignore = true),
    })
    DescuentoClienteEntity toEntity(CreateDescuentoClienteDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "terceroId", source = "entity.tercero.id"),
        @Mapping(target = "terceroNombre", source = "entity.tercero.nombres"),
        @Mapping(target = "terceroDocumento", source = "entity.tercero.numeroDocumento"),
        @Mapping(target = "categoriaId", source = "entity.categoria.id"),
        @Mapping(target = "categoriaNombre", source = "entity.categoria.nombre"),
    })
    DescuentoClienteDto toDto(DescuentoClienteEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "categoria", ignore = true),
    })
    void updateEntityFromDto(UpdateDescuentoClienteDto dto, @MappingTarget DescuentoClienteEntity entity);

    // ========== PRECIO VOLUMEN ==========

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
    })
    PrecioVolumenEntity toEntity(CreatePrecioVolumenDto dto);

    @Mappings({
        @Mapping(target = "empresaId", source = "entity.empresa.id"),
        @Mapping(target = "productoPresentacionId", source = "entity.productoPresentacion.id"),
        @Mapping(target = "productoPresentacionNombre", source = "entity.productoPresentacion.nombre"),
        @Mapping(target = "productoNombre", source = "entity.productoPresentacion.producto.nombre"),
    })
    PrecioVolumenDto toDto(PrecioVolumenEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "productoPresentacion", ignore = true),
    })
    void updateEntityFromDto(UpdatePrecioVolumenDto dto, @MappingTarget PrecioVolumenEntity entity);
}
