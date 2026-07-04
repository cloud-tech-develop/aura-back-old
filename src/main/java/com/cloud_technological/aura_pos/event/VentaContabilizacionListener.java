package com.cloud_technological.aura_pos.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ErrorLogService;

/**
 * Genera el asiento contable de una venta DESPUÉS de que la venta hizo commit.
 * Al correr en AFTER_COMMIT, un fallo en la contabilización no revierte la
 * venta: se registra en ErrorLog para reproceso posterior.
 */
@Component
public class VentaContabilizacionListener {

    @Autowired
    private ContabilidadAutoService contabilidadAutoService;

    @Autowired
    private ErrorLogService errorLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVentaContabilizable(VentaContabilizableEvent event) {
        try {
            contabilidadAutoService.generarDesdeVenta(
                    event.getVentaId(), event.getEmpresaId(), event.getUsuarioId());
        } catch (Exception ex) {
            errorLogService.registrarAsync(
                    "EVENT",
                    "contabilidad/auto/venta/" + event.getVentaId(),
                    500,
                    "Fallo al generar asiento automático de la venta #" + event.getVentaId(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "sistema",
                    "-");
        }
    }
}
