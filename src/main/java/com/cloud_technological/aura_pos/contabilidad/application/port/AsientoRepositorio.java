package com.cloud_technological.aura_pos.contabilidad.application.port;

import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.OrigenDocumento;

/**
 * Puerto de persistencia del agregado. El adapter JPA asigna el número de
 * comprobante (serie por prefijo) y mapea a las entities existentes; el
 * dominio nunca las conoce.
 */
public interface AsientoRepositorio {

    /** ¿Ya existe asiento para este documento? (idempotencia del posting). */
    boolean existePorOrigen(OrigenDocumento origen, Integer empresaId);

    /** Persiste el asiento en el período dado y devuelve su id. */
    Long guardar(Asiento asiento, Integer empresaId, Integer usuarioId, Long periodoId);
}
