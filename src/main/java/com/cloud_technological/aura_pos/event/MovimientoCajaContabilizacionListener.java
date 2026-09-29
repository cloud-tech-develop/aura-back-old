package com.cloud_technological.aura_pos.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ErrorLogService;

/**
 * Genera el asiento de un movimiento de caja manual tras el commit. Al correr en
 * AFTER_COMMIT, un fallo contable no revierte el movimiento de caja: se registra
 * en ErrorLog para reproceso posterior.
 */
@Component
public class MovimientoCajaContabilizacionListener {

    @Autowired
    private ContabilidadAutoService contabilidadAutoService;

    @Autowired
    private ErrorLogService errorLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovimientoCajaContabilizable(MovimientoCajaContabilizableEvent event) {
        try {
            contabilidadAutoService.generarDesdeMovimientoCaja(
                    event.getMovimientoId(), event.getEmpresaId(), event.getUsuarioId());
        } catch (Exception ex) {
            errorLogService.registrarAsync(
                    "EVENT",
                    "contabilidad/auto/movimiento-caja/" + event.getMovimientoId(),
                    500,
                    "Fallo al generar asiento del movimiento de caja #" + event.getMovimientoId(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "sistema",
                    "-");
        }
    }
}
