package com.cloud_technological.aura_pos.sync.nube.manejadores;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.VentaService;
import com.cloud_technological.aura_pos.services.implementations.FacturaVentaService;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.cloud_technological.aura_pos.sync.nube.TraductorIds;

import lombok.RequiredArgsConstructor;

/**
 * Anulación de una venta hecha en la tienda. Si la venta salió de Facturación,
 * se anula la factura (que anula su venta), para que la factura no quede
 * EMITIDA en la nube con la venta ya reversada.
 */
@Component
@RequiredArgsConstructor
public class VentaAnularManejador implements ManejadorComando {

    private final VentaService ventaService;
    private final FacturaVentaService facturaVentaService;
    private final TraductorIds ids;
    private final SyncNubeQueryRepository repo;

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.VENTA_ANULAR;
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        Long ventaId = ids.aNube(nodo.getId(), TiposComando.T_VENTA, c.getIdLocal());
        Optional<Long> factura = repo.facturaDeVenta(ventaId, nodo.getEmpresaId());
        if (factura.isPresent()) facturaVentaService.anular(factura.get(), nodo.getEmpresaId());
        else ventaService.anular(ventaId, nodo.getEmpresaId());
        return Salida.de(ventaId);
    }
}
