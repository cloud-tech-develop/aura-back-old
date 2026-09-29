package com.cloud_technological.aura_pos.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ErrorLogService;

/**
 * Genera el contraasiento de una anulación DESPUÉS de que la anulación hizo
 * commit. Al correr en AFTER_COMMIT, un fallo contable no revierte la anulación:
 * se registra en ErrorLog para reproceso posterior.
 */
@Component
public class ContabilidadReversaListener {

    @Autowired
    private ContabilidadAutoService contabilidadAutoService;

    @Autowired
    private ErrorLogService errorLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReversa(ContabilidadReversaEvent event) {
        try {
            contabilidadAutoService.reversar(
                    event.getOrigenTipo(), event.getOrigenId(),
                    event.getEmpresaId(), event.getUsuarioId());
        } catch (Exception ex) {
            errorLogService.registrarAsync(
                    "EVENT",
                    "contabilidad/auto/reversa/" + event.getOrigenTipo().toLowerCase() + "/" + event.getOrigenId(),
                    500,
                    "Fallo al reversar asiento de la anulación (" + event.getOrigenTipo()
                            + " #" + event.getOrigenId() + ")",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "sistema",
                    "-");
        }
    }
}
