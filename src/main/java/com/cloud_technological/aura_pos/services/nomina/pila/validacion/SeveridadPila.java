package com.cloud_technological.aura_pos.services.nomina.pila.validacion;

/**
 * Severidad de un hallazgo de validación PILA (agente validador, sección 10).
 *
 * <p>El {@code orden} sirve para presentar los hallazgos priorizados:
 * primero los errores, luego lo no evaluable (no aprobado), después las
 * advertencias (incluye riesgos UGPP) y por último la información.
 */
public enum SeveridadPila {
    ERROR(1),
    NO_EVALUABLE(2),
    ADVERTENCIA(3),
    INFORMACION(4);

    private final int orden;

    SeveridadPila(int orden) {
        this.orden = orden;
    }

    public int getOrden() {
        return orden;
    }
}
