package com.cloud_technological.aura_pos.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ErrorLogService;

/**
 * Genera el asiento contable de una compra DESPUÉS de que la compra hizo commit.
 * Al correr en AFTER_COMMIT, un fallo en la contabilización no revierte la
 * compra: se registra en ErrorLog para reproceso posterior.
 */
@Component
public class CompraContabilizacionListener {

    @Autowired
    private ContabilidadAutoService contabilidadAutoService;

    @Autowired
    private ErrorLogService errorLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompraContabilizable(CompraContabilizableEvent event) {
        try {
            // Si la compra ya tenía asiento, viene de una edición: se reversa el
            // vigente antes de generar el nuevo. Sin esto el asiento se quedaba
            // con los valores originales y el documento decía otra cosa.
            //
            // reversar() es no-op cuando no hay asiento vigente, así que el alta
            // normal pasa derecho por aquí.
            contabilidadAutoService.reversar(
                    "COMPRA", event.getCompraId(), event.getEmpresaId(), event.getUsuarioId());
            contabilidadAutoService.generarDesdeCompra(
                    event.getCompraId(), event.getEmpresaId(), event.getUsuarioId());
        } catch (Exception ex) {
            errorLogService.registrarAsync(
                    "EVENT",
                    "contabilidad/auto/compra/" + event.getCompraId(),
                    500,
                    "Fallo al generar asiento automático de la compra #" + event.getCompraId(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "sistema",
                    "-");
        }
    }
}
