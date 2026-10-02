package com.cloud_technological.aura_pos.utils;

import org.springframework.http.HttpStatus;

/**
 * Factus no respondió a tiempo después de recibir la factura: pudo haberla
 * aceptado. No es un error como los demás: la venta queda en estado
 * DESCONOCIDO (no se revierte) para que nadie la reenvíe a ciegas.
 */
public class FacturaEstadoInciertoException extends GlobalException {

    public FacturaEstadoInciertoException(String mensaje) {
        super(HttpStatus.GATEWAY_TIMEOUT, mensaje);
    }
}
