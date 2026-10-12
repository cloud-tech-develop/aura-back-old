package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.documento.CreateDocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.FiltroDocumentoNominaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Prenóminas y nóminas por documento (rediseño R3.2): encabezado con tipo de
 * comprobante, número propio y período de días. Los empleados, la liquidación y la
 * confirmación se agregan en las fases siguientes.
 */
public interface NominaDocumentoService {

    /** Listado paginado con filtros (clase de documento, tipo, estado, rango de fechas). */
    PageImpl<DocumentoNominaDto> listar(PageableDto<FiltroDocumentoNominaDto> pageable, Integer empresaId);

    /** Encabezado; 404 si no es de la empresa. */
    DocumentoNominaDto obtener(Long id, Integer empresaId);

    /**
     * Crea una prenómina preliminar con el siguiente número del tipo de comprobante y
     * la cuelga del período con su mismo rango (lo crea si no existe). 400 si el tipo
     * no es de la empresa o está inactivo, o si las fechas no cuadran.
     */
    DocumentoNominaDto crearPrenomina(CreateDocumentoNominaDto dto, Integer empresaId, Long usuarioId);

    /**
     * Edita el encabezado de una prenómina preliminar. El tipo de comprobante y el
     * período solo se cambian mientras no tenga empleados.
     */
    DocumentoNominaDto actualizar(Long id, CreateDocumentoNominaDto dto, Integer empresaId);

    /** Anula una prenómina preliminar; 409 si ya está confirmada o anulada. */
    DocumentoNominaDto anular(Long id, Integer empresaId);
}
