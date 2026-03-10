package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.ventas.CreateVentaPagoDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaPagoDto;
import com.cloud_technological.aura_pos.entity.VentaPagoEntity;


@Mapper(componentModel = "spring")
public interface VentaPagoMapper {
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "venta", ignore = true),
    })
    VentaPagoEntity toEntity(CreateVentaPagoDto dto);

    VentaPagoDto toDto(VentaPagoEntity entity);
}
