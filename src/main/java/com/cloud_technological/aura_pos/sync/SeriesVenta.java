package com.cloud_technological.aura_pos.sync;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.repositories.ventas.VentaQueryRepository;
import com.cloud_technological.aura_pos.sync.local.EstadoNodoLocal;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Prefijo y consecutivo de una venta nueva, según dónde corre la instancia.
 *
 * <ul>
 * <li><b>Reproducción en la nube</b>: los que trae el nodo; el cliente ya los
 * tiene impresos.</li>
 * <li><b>Nodo local</b>: la serie propia del nodo, que la nube nunca usa.</li>
 * <li><b>Nube</b>: la serie de la sucursal, sin contar las series de los nodos.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SeriesVenta {

    /** Numeración asignada. */
    public record Numero(String prefijo, Long consecutivo) {
    }

    private final SyncProperties props;
    private final VentaQueryRepository ventas;
    private final EstadoNodoLocal estadoNodo;
    private final SyncNubeQueryRepository nube;

    /**
     * Numeración para una venta de la sucursal. Toma el candado de la serie: se
     * llama dentro de la transacción de la venta.
     *
     * @param sucursal  sucursal que vende
     * @param empresaId empresa
     * @return prefijo y consecutivo
     */
    public Numero siguiente(SucursalEntity sucursal, Integer empresaId) {
        SyncContexto.Replay replay = SyncContexto.actual();
        if (replay != null && replay.consecutivo() != null) {
            return new Numero(replay.prefijo(), replay.consecutivo());
        }
        Long sucursalId = Long.valueOf(sucursal.getId());
        if (props.esNodoLocal()) {
            // Sin la serie que asigna la nube, el nodo numeraría con la de la
            // sucursal y desde cero: repetiría facturas que ya existen allá.
            String serie = estadoNodo.prefijoVentas().orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT,
                    "El servidor local todavía no se ha sincronizado con la nube: conéctelo a internet una vez"
                            + " antes de vender"));
            long piso = estadoNodo.pisoConsecutivoVenta(sucursal.getId());
            return new Numero(serie, ventas.obtenerSiguienteConsecutivoSerie(sucursalId, serie, piso));
        }
        return new Numero(sucursal.getPrefijoFacturacion(),
                ventas.obtenerSiguienteConsecutivo(sucursalId, seriesDeNodos(empresaId)));
    }

    /** En la nube, las series reservadas por los nodos de la empresa. */
    private List<String> seriesDeNodos(Integer empresaId) {
        if (!props.esNube()) return List.of();
        return nube.prefijosNodos(empresaId);
    }
}
