package com.cloud_technological.aura_pos.sync.nube.manejadores;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaPagoDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaDto;
import com.cloud_technological.aura_pos.services.VentaService;
import com.cloud_technological.aura_pos.sync.SyncIds;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.cloud_technological.aura_pos.sync.nube.TraductorIds;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Venta del POS hecha en la tienda.
 *
 * <p>Payload: {@code {venta: CreateVentaDto, prefijo, consecutivo}}. El DTO es
 * el que quedó al final de la venta en el nodo (con el recargo por forma de pago
 * ya aplicado). La nube corre el mismo {@code VentaService.crear}: descuenta
 * inventario, crea la cartera y dispara el asiento.
 */
@Component
@RequiredArgsConstructor
public class VentaCrearManejador implements ManejadorComando {

    private final VentaService ventaService;
    private final TraductorIds ids;
    private final SyncNubeQueryRepository repo;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.VENTA_CREAR;
    }

    /** {@inheritDoc} */
    @Override
    public Numeracion numeracion(SyncDtos.Comando comando) {
        return Numeracion.delPayload(comando.getPayload());
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        CreateVentaDto dto;
        try {
            dto = objectMapper.treeToValue(c.getPayload().get("venta"), CreateVentaDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Venta ilegible en el comando: " + e.getMessage(), e);
        }
        traducir(dto, nodo.getId());
        VentaDto venta = ventaService.crear(dto, nodo.getEmpresaId(), c.getUsuarioId());
        repo.guardarMapa(nodo.getId(), TiposComando.T_VENTA, c.getIdLocal(), venta.getId());
        return Salida.de(venta.getId());
    }

    /**
     * Pasa los ids del nodo a los de la nube. Lo que el nodo crea y sincroniza
     * (cliente, turno) se traduce; lo que no viaja (cotización, pedido,
     * autorización hechos en la tienda) se suelta.
     */
    private void traducir(CreateVentaDto dto, Long nodoId) {
        dto.setClienteId(ids.aNube(nodoId, TiposComando.T_TERCERO, dto.getClienteId()));
        dto.setTurnoCajaId(ids.aNube(nodoId, TiposComando.T_TURNO, dto.getTurnoCajaId()));
        dto.setPedidoVendedorId(TraductorIds.soloNube(dto.getPedidoVendedorId()));
        dto.setCotizacionId(TraductorIds.soloNube(dto.getCotizacionId()));
        dto.setAutorizacionId(TraductorIds.soloNube(dto.getAutorizacionId()));
        if (dto.getDetalles() != null) {
            for (CreateVentaDetalleDto d : dto.getDetalles()) {
                d.setCotizacionDetalleId(TraductorIds.soloNube(d.getCotizacionDetalleId()));
                d.setLoteId(TraductorIds.soloNube(d.getLoteId()));
                if (d.getSerialIds() != null)
                    d.setSerialIds(d.getSerialIds().stream().filter(s -> !SyncIds.esLocal(s)).toList());
            }
        }
        if (dto.getPagos() != null) {
            for (CreateVentaPagoDto p : dto.getPagos()) {
                p.setCuentaBancariaId(TraductorIds.soloNube(p.getCuentaBancariaId()));
            }
        }
    }
}
