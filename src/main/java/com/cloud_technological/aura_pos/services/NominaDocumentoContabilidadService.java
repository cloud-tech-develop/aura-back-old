package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.nomina.documento.ProyeccionContableDto;

/**
 * Pestaña Contabilidad de la prenómina/nómina (rediseño R4b): el asiento que se
 * generaría con lo liquidado, sin guardar nada.
 */
public interface NominaDocumentoContabilidadService {

    /**
     * Proyección del asiento del documento, agrupada por cuenta y descripción. Usa
     * el mismo armado del asiento real; lo que impida armarlo (una cuenta sin
     * configurar) va en las advertencias en vez de fallar.
     */
    ProyeccionContableDto proyeccion(Long documentoId, Integer empresaId);
}
