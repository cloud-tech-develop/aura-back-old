package com.cloud_technological.aura_pos.contabilidad.application.exception;

/**
 * No hay período contable abierto para la fecha del asiento, o el período
 * está cerrado. El mensaje siempre dice qué debe hacer el usuario.
 */
public class PeriodoCerradoException extends RuntimeException {

    public PeriodoCerradoException(String message) {
        super(message);
    }
}
