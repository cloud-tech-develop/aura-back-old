package com.cloud_technological.aura_pos.contabilidad.application;

import com.cloud_technological.aura_pos.contabilidad.domain.model.OrigenDocumento;

/**
 * Datos mínimos con los que el caso de uso contabiliza cualquier documento:
 * qué documento es, de qué empresa y quién disparó la operación.
 */
public record ContextoContabilizacion(
        String tipoOrigen,
        Long origenId,
        Integer empresaId,
        Integer usuarioId) {

    public OrigenDocumento origen() {
        return new OrigenDocumento(tipoOrigen, origenId);
    }
}
