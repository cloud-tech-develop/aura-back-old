package com.cloud_technological.aura_pos.sync.nube;

import java.util.List;

import com.cloud_technological.aura_pos.sync.dto.SyncDtos;

/** Lado nube del modo híbrido: atiende a los nodos y administra su registro. */
public interface SyncNubeService {

    /**
     * Nodo dueño del token.
     *
     * @param token token en claro que envía el nodo
     * @return el nodo activo
     * @throws com.cloud_technological.aura_pos.utils.GlobalException 401 si no existe o está inactivo
     */
    SyncDtos.NodoInfo autenticar(String token);

    /**
     * Reproduce los comandos del nodo.
     *
     * @param nodo     nodo autenticado
     * @param peticion comandos en orden
     * @return resultado por comando hasta el primer error
     */
    SyncDtos.PushRespuesta push(SyncDtos.NodoInfo nodo, SyncDtos.PushPeticion peticion);

    /**
     * Lo que el nodo debe tener de la nube.
     *
     * <ul>
     * <li>{@code saldos}: catálogo + existencias + turnos (el nodo lo pide con la
     * cola vacía).</li>
     * <li>{@code soloTurnos}: solo los turnos abiertos; bajada liviana de cada
     * ciclo para que la tienda pueda seguir el turno de la nube si se cae
     * internet.</li>
     * <li>ninguno: solo el catálogo.</li>
     * </ul>
     *
     * @param nodo       nodo autenticado
     * @param saldos     true para incluir inventario, lotes, seriales y turnos
     * @param soloTurnos true para devolver únicamente los turnos
     * @return lo que el nodo debe aplicar
     */
    SyncDtos.PullRespuesta pull(SyncDtos.NodoInfo nodo, boolean saldos, boolean soloTurnos);

    /**
     * Registra un servidor local para la empresa.
     *
     * @param empresaId empresa
     * @param nuevo     datos del nodo
     * @return el nodo y su token (se muestra una sola vez)
     */
    SyncDtos.NodoCreado crearNodo(Integer empresaId, SyncDtos.NodoNuevo nuevo);

    /**
     * Nodos de la empresa.
     *
     * @param empresaId empresa
     * @return nodos registrados
     */
    List<SyncDtos.NodoInfo> nodos(Integer empresaId);

    /**
     * Activa o desactiva un nodo. Desactivado, su token deja de servir.
     *
     * @param id        nodo
     * @param empresaId empresa
     * @param activo    nuevo estado
     */
    void cambiarActivo(Long id, Integer empresaId, boolean activo);
}
