package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.ReportingPolicy;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.CreateVinculacionDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;

/**
 * Formulario de vinculación → contrato laboral. Solo copia los datos editables;
 * empleado, salario, estado, código y los campos derivados (cargo en texto,
 * integral, tarifa ARL) los pone {@code VinculacionServiceImpl}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VinculacionMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "empleado", ignore = true),
        @Mapping(target = "salarioBase", ignore = true),
        @Mapping(target = "estado", ignore = true),
        @Mapping(target = "codigo", ignore = true),
        @Mapping(target = "cargo", ignore = true),
        @Mapping(target = "periodicidad", source = "frecuenciaPago"),
        @Mapping(target = "docInternoUrl", ignore = true),
        @Mapping(target = "docExternoUrl", ignore = true),
    })
    void actualizar(CreateVinculacionDto dto, @MappingTarget ContratoLaboralEntity entity);
}
