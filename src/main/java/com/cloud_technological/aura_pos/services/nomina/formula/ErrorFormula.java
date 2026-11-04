package com.cloud_technological.aura_pos.services.nomina.formula;

/**
 * Error de una fórmula, en palabras para el usuario. {@code posicion} es el
 * carácter (desde 1) donde se detectó; 0 si es de evaluación.
 */
public class ErrorFormula extends RuntimeException {

    private final int posicion;

    public ErrorFormula(String mensaje, int posicion) {
        super(mensaje);
        this.posicion = posicion;
    }

    public int getPosicion() {
        return posicion;
    }
}
