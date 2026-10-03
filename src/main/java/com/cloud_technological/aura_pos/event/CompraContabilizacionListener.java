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
            // vigente y se genera el nuevo, TODO en una transacción (ver
            // reprocesarCompra). Si el nuevo falla, la compra conserva su asiento
            // anterior en vez de quedar sin ninguno. En el alta la reversa es no-op.
            contabilidadAutoService.reprocesarCompra(
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
