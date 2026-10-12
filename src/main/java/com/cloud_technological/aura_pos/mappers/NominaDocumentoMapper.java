package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.nomina.documento.CreateDocumentoNominaDto;
import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;

/**
 * Encabezado de la prenómina → entidad. Solo copia los campos que llegan del
 * formulario; número, período, estado y totales los pone el servicio.
 */
@Mapper(componentModel = "spring")
public interface NominaDocumentoMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
        @Mapping(target = "tipoComprobanteId", source = "tipoComprobanteId"),
        @Mapping(target = "fecha", source = "fecha"),
        @Mapping(target = "fechaDesde", source = "fechaDesde"),
        @Mapping(target = "fechaHasta", source = "fechaHasta"),
        @Mapping(target = "fechaLiquidacion", source = "fechaLiquidacion"),
        @Mapping(target = "fechaPago", source = "fechaPago"),
        @Mapping(target = "simulacion", source = "simulacion"),
        @Mapping(target = "descripcion", source = "descripcion"),
    })
    void copiarEncabezado(CreateDocumentoNominaDto dto, @MappingTarget NominaDocumentoEntity entity);
}
