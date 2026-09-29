package com.cloud_technological.aura_pos.services.nomina.pila.validacion;

/**
 * Estado general de la planilla tras la validación (agente validador, sección 12).
 */
public enum EstadoPlanillaPila {
    BLOQUEADA,
    NO_EVALUABLE,
    LISTA_CON_ADVERTENCIAS,
    LISTA
}
