package com.cloud_technological.aura_pos.sync.nube;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.sync.SyncIds;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Traduce los ids que vienen en un comando del nodo a los de la nube.
 *
 * <p>Un id por debajo de {@link SyncIds#BASE_LOCAL} vino de la nube y se deja
 * igual; uno por encima lo creó el nodo y se busca en {@code sync_id_map}.
 */
@Component
@RequiredArgsConstructor
public class TraductorIds {

    private final SyncNubeQueryRepository repo;

    /**
     * Id de la nube para un id del nodo que debe existir.
     *
     * @param nodoId nodo que envía
     * @param tabla  tabla del registro
     * @param id     id según el nodo
     * @return id en la nube (null si id es null)
     * @throws GlobalException si el registro del nodo todavía no llegó
     */
    public Long aNube(Long nodoId, String tabla, Long id) {
        if (id == null || !SyncIds.esLocal(id)) return id;
        return repo.idNube(nodoId, tabla, id).orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT,
                "El registro " + id + " de " + tabla + " creado en la tienda todavía no llega a la nube"));
    }

    /**
     * Igual que {@link #aNube}, pero devuelve null si el registro del nodo no
     * tiene equivalente (referencias opcionales que no viajan, como una
     * cotización hecha en la tienda).
     */
    public Long aNubeOpcional(Long nodoId, String tabla, Long id) {
        if (id == null || !SyncIds.esLocal(id)) return id;
        return repo.idNube(nodoId, tabla, id).orElse(null);
    }

    /**
     * Para referencias a tablas que no se sincronizan desde el nodo: un id local
     * no significa nada en la nube y se descarta.
     */
    public static Long soloNube(Long id) {
        return SyncIds.esLocal(id) ? null : id;
    }
}
