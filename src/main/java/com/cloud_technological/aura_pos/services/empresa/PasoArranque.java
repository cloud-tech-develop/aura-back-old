package com.cloud_technological.aura_pos.services.empresa;

/**
 * Lo que se siembra cuando una empresa arranca una línea de uso. Cada paso es
 * idempotente: correrlo de nuevo no duplica ni cambia lo que ya existe.
 */
public enum PasoArranque {

    /**
     * PUC completo + configuración contable por concepto, formas de pago,
     * categorías contables, impuestos y mapeos de exógena
     * ({@code PlanCuentasService.seedPUC}).
     */
    CONTABLE("Plan de cuentas y configuración contable");

    private final String nombre;

    PasoArranque(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() { return nombre; }
}
