package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.documento.AgregarNovedadDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.NovedadDocumentoDto;

/**
 * Pestaña Novedades de la prenómina (rediseño R4d). Agregar o quitar una novedad
 * usa las mismas reglas de la liquidación por períodos (días, incapacidades,
 * vacaciones, topes de la vinculación) y deja al empleado sin liquidar para que
 * se reliquide.
 */
public interface NominaDocumentoNovedadService {

    /** Novedades de los empleados del documento. */
    List<NovedadDocumentoDto> listar(Long documentoId, Integer empresaId);

    /** Agrega una novedad a un empleado de la prenómina preliminar. */
    List<NovedadDocumentoDto> agregar(Long documentoId, AgregarNovedadDocumentoDto dto, Integer empresaId);

    /** Quita una novedad de un empleado de la prenómina preliminar. */
    List<NovedadDocumentoDto> eliminar(Long documentoId, Long novedadId, Integer empresaId);
}
