package com.cloud_technological.aura_pos.sync.nube.manejadores;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_technological.aura_pos.dto.factus.FacturaElectronicaResponseDto;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.VentaFacturaService;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.TraductorIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Factura electrónica de una venta hecha en la tienda.
 *
 * <p>Solo la nube habla con Factus: así una venta nunca se transmite dos veces,
 * una desde el nodo y otra desde la nube.
 *
 * <p>Un rechazo de Factus o de la DIAN no detiene la cola: la venta ya está en
 * la nube y la factura se corrige y reenvía desde allí. Por eso este manejador
 * siempre responde bien, con el estado DIAN que haya quedado.
 */
@Slf4j
@Component
public class FacturaElectronicaManejador implements ManejadorComando {

    private final VentaFacturaService ventaFacturaService;
    private final VentaJPARepository ventas;
    private final TraductorIds ids;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate aparte;

    public FacturaElectronicaManejador(VentaFacturaService ventaFacturaService, VentaJPARepository ventas,
            TraductorIds ids, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.ventaFacturaService = ventaFacturaService;
        this.ventas = ventas;
        this.ids = ids;
        this.objectMapper = objectMapper;
        // El envío a Factus confirma (o deja DESCONOCIDO) por su cuenta: si fallara
        // dentro de la transacción del comando, la dejaría marcada para revertir.
        this.aparte = new TransactionTemplate(transactionManager);
        this.aparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.FE_EMITIR;
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        Long ventaId = ids.aNube(nodo.getId(), TiposComando.T_VENTA, c.getIdLocal());
        ObjectNode datos = objectMapper.createObjectNode();
        try {
            FacturaElectronicaResponseDto r = aparte.execute(
                    s -> ventaFacturaService.generarFacturaElectronica(ventaId, nodo.getEmpresaId()));
            datos = objectMapper.valueToTree(r);
        } catch (Exception e) {
            log.warn("[SYNC] Factura electrónica de la venta {} no emitida: {}", ventaId, e.getMessage());
            datos.put("mensaje", e.getMessage());
        }
        // Estado real en la base: cubre "ya estaba emitida" (reintento) y DESCONOCIDO.
        VentaEntity v = aparte.execute(s -> ventas.findByIdAndEmpresaId(ventaId, nodo.getEmpresaId()).orElse(null));
        if (v != null) {
            datos.put("estadoDian", v.getEstadoDian() != null ? v.getEstadoDian() : "ERROR");
            datos.put("cufe", v.getCufe());
            datos.put("qr", v.getQrData());
            datos.put("pdfUrl", v.getFactusUrl());
            datos.put("facturaNumero", v.getFactusNumero());
        }
        return new Salida(ventaId, datos);
    }
}
