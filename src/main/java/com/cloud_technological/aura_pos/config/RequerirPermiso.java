package com.cloud_technological.aura_pos.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para validar permisos de módulo/submódulo en controladores
 * HU-031: Validación de Permisos en Controladores
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequerirPermiso {
    String modulo() default "";
    String submodulo() default "";
    String mensaje() default "No tienes permiso para acceder a este recurso";
}
