package com.cloud_technological.aura_pos.sync.nube;

import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reproduce en la nube un tipo de comando del nodo.
 *
 * <p>Corre dentro de la transacción que abre {@code ProcesadorComandos}, con el
 * usuario del comando autenticado y {@code SyncContexto} activo. Lanzar una
 * excepción revierte todo lo que hizo.
 */
public interface ManejadorComando {

    /** Resultado de la reproducción. */
    record Salida(Long idNube, JsonNode datos) {
        public static Salida de(Long idNube) {
            return new Salida(idNube, null);
        }
    }

    /**
     * Tipo de comando que atiende.
     *
     * @return uno de {@code TiposComando}
     */
    String tipo();

    /**
     * Reproduce el comando.
     *
     * @param comando lo que envió el nodo
     * @param nodo    nodo que lo envió
     * @return id del registro en la nube y datos para el nodo
     */
    Salida reproducir(SyncDtos.Comando comando, SyncDtos.NodoInfo nodo);

    /** Prefijo y consecutivo que el documento tiene en el nodo. */
    record Numeracion(String prefijo, Long consecutivo) {
        public static final Numeracion NINGUNA = new Numeracion(null, null);

        /** Lee {@code prefijo} y {@code consecutivo} del payload. */
        public static Numeracion delPayload(JsonNode payload) {
            String prefijo = payload.hasNonNull("prefijo") ? payload.get("prefijo").asText() : null;
            Long consecutivo = payload.hasNonNull("consecutivo") ? payload.get("consecutivo").asLong() : null;
            return new Numeracion(prefijo, consecutivo);
        }
    }

    /**
     * Numeración del documento que el comando reproduce, para que la nube la
     * conserve. Por defecto, ninguna.
     *
     * @param comando lo que envió el nodo
     * @return prefijo y consecutivo del nodo
     */
    default Numeracion numeracion(SyncDtos.Comando comando) {
        return Numeracion.NINGUNA;
    }
}
