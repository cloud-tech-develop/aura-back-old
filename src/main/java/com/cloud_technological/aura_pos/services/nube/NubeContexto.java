package com.cloud_technological.aura_pos.services.nube;

/**
 * Quién hace la petición, armado por el controlador SOLO con datos del token y
 * de la conexión (nunca del cuerpo ni de los parámetros).
 *
 * @param empresaId empresa del token: la única cuyo espacio se toca
 * @param usuarioId usuario del token
 * @param firmaId   firma contable por la que entró; null = usuario de la propia empresa
 * @param ip        IP de origen (para la bitácora)
 * @param userAgent navegador (para la bitácora)
 */
public record NubeContexto(Integer empresaId, Integer usuarioId, Integer firmaId, String ip, String userAgent) {

    /** ¿Entró por la firma? Solo la firma ve y crea carpetas SOLO_FIRMA. */
    public boolean esFirma() {
        return firmaId != null;
    }
}
