package com.cloud_technological.aura_pos.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ErrorLogService;

/**
 * Genera el asiento contable de una devolución DESPUÉS de que la devolución hizo
 * commit. Al correr en AFTER_COMMIT, un fallo contable no revierte la
 * devolución: se registra en ErrorLog para reproceso posterior.
 */
@Component
public class DevolucionContabilizacionListener {

    @Autowired
    private ContabilidadAutoService contabilidadAutoService;

    @Autowired
    private ErrorLogService errorLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDevolucionContabilizable(DevolucionContabilizableEvent event) {
        try {
            contabilidadAutoService.generarDesdeDevolucion(
                    event.getDevolucionId(), event.getEmpresaId(), event.getUsuarioId());
        } catch (Exception ex) {
            errorLogService.registrarAsync(
                    "EVENT",
                    "contabilidad/auto/devolucion/" + event.getDevolucionId(),
                    500,
                    "Fallo al generar asiento automático de la devolución #" + event.getDevolucionId(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "sistema",
                    "-");
        }
    }
}
