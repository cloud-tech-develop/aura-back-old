package com.cloud_technological.aura_pos.sync.local;

import java.util.List;

import com.cloud_technological.aura_pos.sync.dto.SyncDtos;

/** Lado nodo del modo híbrido: lo que ve y hace la pantalla de sincronización. */
public interface SyncLocalService {

    /**
     * Estado para el indicador del front (en línea, pendientes, errores).
     *
     * @return estado del nodo; en la nube, solo el modo
     */
    SyncDtos.EstadoLocal estado();

    /**
     * Últimos comandos de la cola, los pendientes primero.
     *
     * @param limite cuántos
     */
    List<SyncDtos.FilaCola> cola(int limite);

    /**
     * Sincroniza ya: sube la cola y baja catálogo y existencias.
     *
     * @return el estado después de intentarlo
     */
    SyncDtos.EstadoLocal sincronizarAhora();

    /**
     * Saca de la cola un comando en error que no se va a resolver reintentando.
     * La operación queda solo en la tienda: hay que registrarla a mano en la nube.
     *
     * @param id fila de sync_outbox
     */
    void descartar(Long id);
}
