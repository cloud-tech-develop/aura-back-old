package com.cloud_technological.aura_pos.sync.local;

/**
 * Cola de salida del nodo local: lo que pasó en la tienda y debe llegar a la nube.
 */
public interface SyncOutboxService {

    /**
     * Encola una operación para la nube, dentro de la transacción en curso.
     *
     * <p>No hace nada si esta instancia es la nube o si se está reproduciendo un
     * comando (lo reproducido ya viene de un nodo). Por eso los servicios lo
     * pueden llamar siempre, sin preguntar el modo.
     *
     * @param tipo      tipo de comando ({@code TiposComando})
     * @param idLocal   id del registro principal en el nodo
     * @param empresaId empresa
     * @param usuarioId usuario que hizo la operación
     * @param payload   datos para reproducirla; se guarda como JSON
     */
    void registrar(String tipo, Long idLocal, Integer empresaId, Long usuarioId, Object payload);

    /**
     * Indica si las operaciones se están encolando (nodo local fuera de replay).
     *
     * @return true si {@link #registrar} guarda algo
     */
    boolean activo();
}
