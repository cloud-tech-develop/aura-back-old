package com.cloud_technological.aura_pos.contabilidad.domain;

/**
 * El asiento viola una invariante contable (descuadre, sin movimientos,
 * partidas insuficientes). Es imposible persistir un asiento que la lance.
 */
public class AsientoDescuadradoException extends RuntimeException {

    public AsientoDescuadradoException(String message) {
        super(message);
    }
}
