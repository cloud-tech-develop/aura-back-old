package com.cloud_technological.aura_pos.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ErrorLogService;

/**
 * Genera el asiento contable de un gasto o una merma DESPUÉS de su commit.
 * Un fallo contable no revierte la operación: se registra en ErrorLog.
 */
@Component
public class OperacionContabilizacionListener {

    @Autowired
    private ContabilidadAutoService contabilidadAutoService;

    @Autowired
    private ErrorLogService errorLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperacion(OperacionContabilizableEvent event) {
        try {
            switch (event.getTipo()) {
                case "GASTO" -> contabilidadAutoService.generarDesdeGasto(
                        event.getOrigenId(), event.getEmpresaId(), event.getUsuarioId());
                case "NOMINA" -> contabilidadAutoService.generarDesdeNomina(
                        event.getOrigenId(), event.getEmpresaId(), event.getUsuarioId());
                case "OBLIGACION" -> contabilidadAutoService.generarDesdeObligacion(
                        event.getOrigenId(), event.getEmpresaId(), event.getUsuarioId());
                case "CUOTA" -> contabilidadAutoService.generarDesdePagoCuota(
                        event.getOrigenId(), event.getEmpresaId(), event.getUsuarioId());
                default -> contabilidadAutoService.generarDesdeMerma(
                        event.getOrigenId(), event.getEmpresaId(), event.getUsuarioId());
            }
        } catch (Exception ex) {
            errorLogService.registrarAsync(
                    "EVENT",
                    "contabilidad/auto/" + event.getTipo().toLowerCase() + "/" + event.getOrigenId(),
                    500,
                    "Fallo al generar asiento de " + event.getTipo() + " #" + event.getOrigenId(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    "sistema",
                    "-");
        }
    }
}
