package com.cloud_technological.aura_pos.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ErrorLogService;

/**
 * Genera el asiento contable de un abono (cobro de cartera o pago a proveedor)
 * DESPUÉS de que el abono hizo commit. Un fallo contable no revierte el abono:
 * se registra en ErrorLog para reproceso.
 */
@Component
public class AbonoContabilizacionListener {

    @Autowired
    private ContabilidadAutoService contabilidadAutoService;

    @Autowired
    private ErrorLogService errorLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAbono(AbonoContabilizableEvent event) {
        try {
            if ("COBRO".equals(event.getTipo())) {
                contabilidadAutoService.generarDesdeAbonoCobro(
                        event.getAbonoId(), event.getEmpresaId(), event.getUsuarioId());
            } else {
                contabilidadAutoService.generarDesdeAbonoPago(
                        event.getAbonoId(), event.getEmpresaId(), event.getUsuarioId());
            }
        } catch (Exception ex) {
            errorLogService.registrarAsync(
                    "EVENT",
                    "contabilidad/auto/abono/" + event.getTipo().toLowerCase() + "/" + event.getAbonoId(),
                    500,
                    "Fallo al generar asiento del abono (" + event.getTipo() + " #" + event.getAbonoId() + ")",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "sistema",
                    "-");
        }
    }
}
