package com.cloud_technological.aura_pos.sync;

/**
 * Rango de ids del nodo local.
 *
 * <p>Al preparar la base de un nodo, todas sus secuencias arrancan en
 * {@link #BASE_LOCAL} (scripts/nodo-local/preparar_nodo.sql). Lo que baja de la
 * nube conserva su id, siempre menor; lo que se crea en la tienda queda por
 * encima. Así un registro bajado y uno creado sin internet nunca chocan, y la
 * nube sabe qué ids del nodo debe traducir.
 *
 * <p>El valor cabe en un {@code Integer} a propósito: hay entidades que mapean
 * el id como Integer.
 */
public final class SyncIds {

    public static final long BASE_LOCAL = 1_500_000_000L;

    private SyncIds() {
    }

    /**
     * Indica si el id nació en un nodo local.
     *
     * @param id id a revisar; null no es local
     * @return true si está en el rango del nodo
     */
    public static boolean esLocal(Long id) {
        return id != null && id >= BASE_LOCAL;
    }

    /**
     * Variante para ids Integer.
     *
     * @param id id a revisar; null no es local
     * @return true si está en el rango del nodo
     */
    public static boolean esLocal(Integer id) {
        return id != null && id.longValue() >= BASE_LOCAL;
    }
}
