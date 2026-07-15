package com.cloud_technological.aura_pos.contabilidad.application.exception;

/**
 * Un concepto contable no resuelve a ninguna cuenta activa del PUC de la
 * empresa. El mensaje dice QUÉ configurar, nunca un stacktrace.
 */
public class CuentaNoParametrizadaException extends RuntimeException {

    public CuentaNoParametrizadaException(String message) {
        super(message);
    }
}
