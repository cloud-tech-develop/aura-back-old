package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.entity.NominaDetalleEntity;
import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;

/**
 * Copia de la prenómina confirmada a la nómina (rediseño R4a): encabezado, filas de
 * empleado, novedades y detalle. Ids, relaciones con el documento nuevo, estados y
 * fechas de auditoría los pone el servicio.
 */
@Mapper(componentModel = "spring")
public interface NominaCopiaMapper {

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "claseDocumento", ignore = true),
        @Mapping(target = "numero", ignore = true),
        @Mapping(target = "estado", ignore = true),
        @Mapping(target = "fechaConfirmacion", ignore = true),
        @Mapping(target = "documentoOrigenId", ignore = true),
        @Mapping(target = "usuarioId", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
    })
    NominaDocumentoEntity copiarEncabezado(NominaDocumentoEntity origen);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "documentoId", ignore = true),
        @Mapping(target = "esPrenomina", ignore = true),
        @Mapping(target = "estado", ignore = true),
        @Mapping(target = "novedades", ignore = true),
        @Mapping(target = "medioPago", ignore = true),
        @Mapping(target = "cuentaBancariaId", ignore = true),
        @Mapping(target = "cuentaPagoId", ignore = true),
        @Mapping(target = "fechaPago", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
    })
    NominaEntity copiarFila(NominaEntity origen);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "nomina", ignore = true),
    })
    NominaNovedadEntity copiarNovedad(NominaNovedadEntity origen);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "nomina", ignore = true),
        @Mapping(target = "novedad", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
    })
    NominaDetalleEntity copiarDetalle(NominaDetalleEntity origen);
}
