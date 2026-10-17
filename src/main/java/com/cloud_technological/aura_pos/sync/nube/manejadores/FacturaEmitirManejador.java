package com.cloud_technological.aura_pos.sync.nube.manejadores;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.factura_venta.FacturaVentaDtos;
import com.cloud_technological.aura_pos.services.implementations.FacturaVentaService;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.cloud_technological.aura_pos.sync.nube.TraductorIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Factura de Facturación (ERP) emitida en la tienda.
 *
 * <p>Payload: {@code {factura: Guardar, ventaIdLocal, prefijo, consecutivo}}.
 * La nube crea el borrador y lo emite con el mismo servicio, conservando la
 * numeración de la venta que el nodo ya le dio al cliente. Los borradores no
 * viajan: solo importa la factura emitida.
 */
@Component
@RequiredArgsConstructor
public class FacturaEmitirManejador implements ManejadorComando {

    private final FacturaVentaService facturaVentaService;
    private final TraductorIds ids;
    private final SyncNubeQueryRepository repo;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.FACTURA_EMITIR;
    }

    /** {@inheritDoc} */
    @Override
    public Numeracion numeracion(SyncDtos.Comando comando) {
        return Numeracion.delPayload(comando.getPayload());
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        JsonNode p = c.getPayload();
        FacturaVentaDtos.Guardar dto;
        try {
            dto = objectMapper.treeToValue(p.get("factura"), FacturaVentaDtos.Guardar.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Factura ilegible en el comando: " + e.getMessage(), e);
        }
        dto.setClienteId(ids.aNube(nodo.getId(), TiposComando.T_TERCERO, dto.getClienteId()));
        if (dto.getLineas() != null) {
            for (FacturaVentaDtos.Linea l : dto.getLineas()) {
                l.setCotizacionDetalleId(TraductorIds.soloNube(l.getCotizacionDetalleId()));
            }
        }

        FacturaVentaDtos.Detalle borrador = facturaVentaService.crear(dto, nodo.getEmpresaId(), c.getUsuarioId());
        FacturaVentaDtos.Detalle emitida = facturaVentaService.emitir(borrador.getId(), nodo.getEmpresaId(), c.getUsuarioId());

        repo.guardarMapa(nodo.getId(), TiposComando.T_FACTURA, c.getIdLocal(), emitida.getId());
        if (p.hasNonNull("ventaIdLocal") && emitida.getVentaId() != null)
            repo.guardarMapa(nodo.getId(), TiposComando.T_VENTA, p.get("ventaIdLocal").asLong(), emitida.getVentaId());
        return Salida.de(emitida.getId());
    }
}
