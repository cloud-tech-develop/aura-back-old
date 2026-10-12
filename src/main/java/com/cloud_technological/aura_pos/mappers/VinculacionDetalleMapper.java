package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateNovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateVinculacionConceptoDto;
import com.cloud_technological.aura_pos.entity.VinculacionConceptoEntity;
import com.cloud_technological.aura_pos.entity.VinculacionNovedadPermitidaEntity;

/** DTO → entidad de los conceptos y novedades permitidas de la vinculación. */
@Mapper(componentModel = "spring")
public interface VinculacionDetalleMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "contratoId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
    })
    void actualizarConcepto(CreateVinculacionConceptoDto dto, @MappingTarget VinculacionConceptoEntity entity);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "contratoId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
    })
    void actualizarNovedad(CreateNovedadPermitidaDto dto, @MappingTarget VinculacionNovedadPermitidaEntity entity);
}
