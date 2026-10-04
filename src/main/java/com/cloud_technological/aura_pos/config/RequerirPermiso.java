package com.cloud_technological.aura_pos.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Corrige, para un endpoint o un controller, lo que {@code PermisoRutas} deduce
 * de la ruta y el método (docs/PLAN_PERMISOS.md, fase P3). Solo hace falta cuando
 * la regla general se equivoca.
 *
 * <pre>
 * &#64;RequerirPermiso(claves = "contabilidad.asientos-contables", accion = "VER")  // un POST que solo consulta
 * &#64;RequerirPermiso(libre = true)                                             // lo usa cualquier usuario
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequerirPermiso {

    /** Claves {@code modulo.submodulo} aceptadas (basta una). Vacío = las de PermisoRutas. */
    String[] claves() default {};

    /**
     * VER, CREAR, EDITAR o ANULAR. Vacío = la que se deduce del método y la ruta.
     * Cualquier otro código (REABRIR, APROBAR…) es una acción especial
     * {@code modulo.submodulo:CODIGO} del catálogo {@code permiso_accion_especial} (V192).
     */
    String accion() default "";

    /** No se revisa: cualquier usuario autenticado de la empresa lo puede llamar. */
    boolean libre() default false;
}
