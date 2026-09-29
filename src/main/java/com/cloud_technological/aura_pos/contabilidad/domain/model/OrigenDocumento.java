package com.cloud_technological.aura_pos.contabilidad.domain.model;

/**
 * Identidad del documento operativo que originó un asiento
 * (trazabilidad doble vía documento ⇄ asiento e idempotencia del posting).
 */
public record OrigenDocumento(String tipoOrigen, Long origenId) {

    public OrigenDocumento {
        if (tipoOrigen == null || tipoOrigen.isBlank()) {
            throw new IllegalArgumentException("El asiento requiere un tipo de origen");
        }
        if (origenId == null) {
            throw new IllegalArgumentException("El asiento requiere el id del documento origen");
        }
    }
}
