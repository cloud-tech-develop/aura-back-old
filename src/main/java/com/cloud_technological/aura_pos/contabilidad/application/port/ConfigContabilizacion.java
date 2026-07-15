package com.cloud_technological.aura_pos.contabilidad.application.port;

import com.cloud_technological.aura_pos.contabilidad.domain.model.EstadoAsiento;

/**
 * Puerto del modo de contabilización de la empresa (E3):
 * AUTOMATICO → los asientos nacen CONTABILIZADO;
 * REVISION → nacen BORRADOR y el contador los aprueba.
 */
public interface ConfigContabilizacion {

    /** Estado inicial de los asientos automáticos de la empresa. */
    EstadoAsiento estadoInicial(Integer empresaId);
}
