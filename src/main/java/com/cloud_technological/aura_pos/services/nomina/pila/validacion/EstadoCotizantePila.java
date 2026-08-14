package com.cloud_technological.aura_pos.services.nomina.pila.validacion;

/**
 * Estado general de un cotizante tras la validación (agente validador, sección 11).
 *
 * <ul>
 *   <li>{@code BLOQUEADO} — tiene al menos un ERROR. No debe ir al archivo.</li>
 *   <li>{@code NO_EVALUABLE} — falta información indispensable. No aprobado.</li>
 *   <li>{@code APTO_CON_ADVERTENCIAS} — sin errores, pero requiere revisión.</li>
 *   <li>{@code APTO} — cumple las validaciones aplicables.</li>
 * </ul>
 */
public enum EstadoCotizantePila {
    BLOQUEADO,
    NO_EVALUABLE,
    APTO_CON_ADVERTENCIAS,
    APTO
}
