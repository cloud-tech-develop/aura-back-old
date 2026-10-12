package com.cloud_technological.aura_pos.services.nomina.comprobante;

/**
 * Qué liquida un tipo de comprobante de nómina (docs/PLAN_NOMINA_REDISENO.md §4).
 */
public enum ClaseComprobanteNomina {

    /** Conceptos permanentes + novedades del período. Único que lleva frecuencia. */
    PERIODICO,
    /** Prima de servicios del semestre. */
    PRIMA,
    /** Cesantías consolidadas del año (se consignan al fondo). */
    CESANTIAS,
    /** Intereses sobre cesantías (se pagan al empleado). */
    INTERESES_CESANTIAS,
    /** Retiro: prestaciones, vacaciones, indemnización y descuentos pendientes. */
    LIQUIDACION_DEFINITIVA,
    /** Diferencias por un incremento con fecha anterior. */
    RETROACTIVO,
    /** Bonificaciones y pagos fuera de la nómina ordinaria. */
    OTROS_PAGOS;

    /** ¿La clase se liquida por período (y por eso lleva frecuencia)? */
    public boolean esPeriodica() {
        return this == PERIODICO;
    }
}
