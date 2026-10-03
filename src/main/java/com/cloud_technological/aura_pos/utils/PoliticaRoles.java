package com.cloud_technological.aura_pos.utils;

import java.util.Set;

import org.springframework.http.HttpStatus;

/**
 * Quién puede asignar qué rol desde los endpoints de una empresa.
 *
 * <p>El rol del usuario se convierte tal cual en su authority de Spring
 * ({@code CustomUserDetailsService}), y {@code /api/platform/**} se abre a
 * {@code PLATFORM_ADMIN}. Sin este control un ADMIN de cualquier cliente podía
 * crearse (o editarse) un usuario PLATFORM_ADMIN y administrar todas las
 * empresas, o subirse a SUPER_ADMIN por encima del dueño.
 *
 * <p>No es una lista blanca estricta a propósito: al crear un usuario desde un
 * empleado el rol toma el nombre del tipo de empleado (GERENTE, OFICIOS…), y
 * esos roles no dan privilegios. Lo que se bloquea son los dos roles con poder:
 * <ul>
 *   <li>{@code PLATFORM_ADMIN}: nunca se asigna ni se quita desde una empresa;
 *       solo desde la plataforma o por SQL.</li>
 *   <li>{@code SUPER_ADMIN}: solo otro SUPER_ADMIN lo da o se lo quita a alguien.</li>
 * </ul>
 */
public final class PoliticaRoles {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Roles del sistema: se guardan en mayúsculas para que coincidan con las authorities. */
    private static final Set<String> ROLES_SISTEMA =
            Set.of(PLATFORM_ADMIN, SUPER_ADMIN, "ADMIN", "SUPERVISOR", "CAJERO", "VENDEDOR");

    private PoliticaRoles() {
    }

    /**
     * Valida el cambio de rol y devuelve el rol a guardar.
     *
     * @param rolActual     rol que tiene hoy el usuario; null si se está creando
     * @param rolNuevo      rol pedido; vacío o null = no se cambia
     * @param rolQuienAsigna rol del usuario autenticado que hace el cambio
     * @throws GlobalException 403 si el cambio no está permitido
     */
    public static String validarAsignacion(String rolActual, String rolNuevo, String rolQuienAsigna) {
        if (rolNuevo == null || rolNuevo.isBlank()) {
            if (rolActual == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El rol es obligatorio");
            }
            return rolActual;
        }

        String nuevo = canonico(rolNuevo);
        if (rolActual != null && nuevo.equals(canonico(rolActual))) {
            return rolActual; // sin cambio
        }

        if (rolActual != null && PLATFORM_ADMIN.equals(canonico(rolActual))) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "El rol de un administrador de plataforma no se modifica desde la empresa");
        }
        if (PLATFORM_ADMIN.equals(nuevo)) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "El rol PLATFORM_ADMIN no se puede asignar desde la empresa");
        }

        boolean asignaSuperAdmin = SUPER_ADMIN.equals(canonico(rolQuienAsigna));
        if (SUPER_ADMIN.equals(nuevo) && !asignaSuperAdmin) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "Solo un SUPER_ADMIN puede asignar el rol SUPER_ADMIN");
        }
        if (rolActual != null && SUPER_ADMIN.equals(canonico(rolActual)) && !asignaSuperAdmin) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "Solo un SUPER_ADMIN puede cambiarle el rol a otro SUPER_ADMIN");
        }
        return nuevo;
    }

    /** true si el rol es PLATFORM_ADMIN o SUPER_ADMIN. */
    public static boolean esPrivilegiado(String rol) {
        String r = canonico(rol);
        return PLATFORM_ADMIN.equals(r) || SUPER_ADMIN.equals(r);
    }

    /** Roles del sistema en mayúsculas; cualquier otro (cargo) solo sin espacios sobrantes. */
    private static String canonico(String rol) {
        if (rol == null) return "";
        String r = rol.trim();
        String upper = r.toUpperCase();
        return ROLES_SISTEMA.contains(upper) ? upper : r;
    }
}
