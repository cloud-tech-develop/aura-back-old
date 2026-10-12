package com.cloud_technological.aura_pos.services.nomina.documento;

/**
 * Cómo se agregan empleados a una prenómina (docs/PLAN_NOMINA_REDISENO.md §5.2).
 */
public enum TipoListaNomina {

    /** Las vinculaciones vigentes de uno o varios empleados (ids = terceros). */
    EMPLEADO,
    /** Vinculaciones puntuales (ids = contratos). */
    VINCULACION,
    /** Las vinculaciones de uno o varios grupos de nómina (ids = catálogo GRUPO_NOMINA). */
    GRUPO,
    /** Las vinculaciones de uno o varios tipos de vinculación (ids = tipos). */
    TIPO_VINCULACION,
    /** Todas las vinculaciones activas que admite el comprobante (sin ids). */
    TODOS;

    /** ¿El tipo de lista necesita que el usuario elija algo? */
    public boolean exigeIds() {
        return this != TODOS;
    }
}
