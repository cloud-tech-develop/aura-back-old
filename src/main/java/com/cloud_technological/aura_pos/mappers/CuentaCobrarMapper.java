package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;

@Mapper(componentModel = "spring")
public interface CuentaCobrarMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "venta", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    CuentaCobrarEntity toEntity(CreateCuentaCobrarDto dto);

    @Mappings({
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "ventaId", ignore = true),
        @Mapping(target = "terceroId", ignore = true),
        @Mapping(target = "clienteNombre", ignore = true),
        @Mapping(target = "clienteDocumento", ignore = true),
    })
    CuentaCobrarDto toDto(CuentaCobrarEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "venta", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "totalDeuda", ignore = true),
        @Mapping(target = "totalAbonado", ignore = true),
        @Mapping(target = "saldoPendiente", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(CreateCuentaCobrarDto dto, @MappingTarget CuentaCobrarEntity entity);
}
