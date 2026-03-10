package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.entity.CuentaPagarEntity;

@Mapper(componentModel = "spring")
public interface CuentaPagarMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "compra", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    CuentaPagarEntity toEntity(CreateCuentaPagarDto dto);

    @Mappings({
        @Mapping(target = "empresaId", ignore = true),
        @Mapping(target = "compraId", ignore = true),
        @Mapping(target = "terceroId", ignore = true),
        @Mapping(target = "proveedorNombre", ignore = true),
        @Mapping(target = "proveedorDocumento", ignore = true),
    })
    CuentaPagarDto toDto(CuentaPagarEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "compra", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "totalDeuda", ignore = true),
        @Mapping(target = "totalAbonado", ignore = true),
        @Mapping(target = "saldoPendiente", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void updateEntityFromDto(CreateCuentaPagarDto dto, @MappingTarget CuentaPagarEntity entity);
}
