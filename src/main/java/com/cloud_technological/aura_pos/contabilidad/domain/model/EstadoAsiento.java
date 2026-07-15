package com.cloud_technological.aura_pos.contabilidad.domain.model;

/**
 * Ciclo de vida de un asiento contable.
 * BORRADOR no suma en reportes oficiales; CONTABILIZADO es inmutable
 * (solo se corrige por contraasiento, nunca editando).
 */
public enum EstadoAsiento {
    BORRADOR,
    CONTABILIZADO,
    ANULADO,
    REVERTIDO
}
