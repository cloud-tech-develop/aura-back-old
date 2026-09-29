package com.cloud_technological.aura_pos.contabilidad.infrastructure.event;

/**
 * EL evento único de contabilización (ADR-003): reemplaza los pares
 * evento/listener por origen. Los servicios de negocio lo publican al final
 * del método, con la transacción aún abierta; el listener lo procesa
 * AFTER_COMMIT. Los eventos legacy se deprecan a medida que cada etapa
 * migra su generador.
 */
public record DocumentoContabilizableEvent(
        String tipoOrigen,
        Long origenId,
        Integer empresaId,
        Integer usuarioId) {
}
