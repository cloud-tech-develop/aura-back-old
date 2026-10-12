package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateFamiliarDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.ExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.FamiliarDto;
import com.cloud_technological.aura_pos.entity.EmpleadoCuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoExperienciaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoFamiliarEntity;

/** Entidad ↔ DTO de la hoja de vida del empleado. */
@Mapper(componentModel = "spring")
public interface EmpleadoHojaVidaMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empleadoId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
    })
    void actualizarCuenta(CreateCuentaBancariaDto dto, @MappingTarget EmpleadoCuentaBancariaEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empleadoId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
    })
    void actualizarFamiliar(CreateFamiliarDto dto, @MappingTarget EmpleadoFamiliarEntity entity);

    FamiliarDto aFamiliarDto(EmpleadoFamiliarEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "empleadoId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
    })
    void actualizarExperiencia(CreateExperienciaDto dto, @MappingTarget EmpleadoExperienciaEntity entity);

    ExperienciaDto aExperienciaDto(EmpleadoExperienciaEntity entity);
}
