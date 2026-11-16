package com.cloud_technological.aura_pos.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ArchivoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.CarpetaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.EventoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitudEspacioDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitudPlataformaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.TramoRutaDto;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.ArchivoFila;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.CarpetaFila;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.EventoFila;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.SolicitudFila;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Tramo;

/**
 * Filas de la nube → DTO. Las filas ya no traen la clave del objeto ni el IV, así
 * que nada de eso puede salir por error en una respuesta.
 */
@Mapper(componentModel = "spring")
public interface NubeMapper {

    @Mapping(target = "creadaPorFirma", expression = "java(fila.creadoPorFirmaId() != null)")
    CarpetaDto toDto(CarpetaFila fila);

    List<CarpetaDto> toCarpetas(List<CarpetaFila> filas);

    @Mapping(target = "subidoPorFirma", expression = "java(fila.subidoPorFirmaId() != null)")
    ArchivoDto toDto(ArchivoFila fila);

    List<ArchivoDto> toArchivos(List<ArchivoFila> filas);

    TramoRutaDto toDto(Tramo tramo);

    List<TramoRutaDto> toRuta(List<Tramo> tramos);

    @Mapping(target = "porFirma", expression = "java(fila.firmaEmpresaId() != null)")
    EventoDto toDto(EventoFila fila);

    List<EventoDto> toEventos(List<EventoFila> filas);

    SolicitudEspacioDto toSolicitud(SolicitudFila fila);

    @Mapping(target = "solicitadoPor", source = "solicitadoPorNombre")
    SolicitudPlataformaDto toPlataforma(SolicitudFila fila);

    List<SolicitudPlataformaDto> toPlataforma(List<SolicitudFila> filas);
}
