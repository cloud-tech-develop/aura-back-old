package com.cloud_technological.aura_pos.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.services.implementations.ReglaCreditoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Después del commit del pago o de la venta: recalcula el score y corre las
 * reglas del evento. Si falla, el documento ya quedó guardado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditoMovimientoListener {

    private final ReglaCreditoService reglaCreditoService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMovimiento(CreditoMovimientoEvent e) {
        try {
            reglaCreditoService.evaluarCliente(e.terceroId(), e.empresaId(), e.evento(), true);
        } catch (Exception ex) {
            log.warn("No se pudieron evaluar las reglas de crédito del tercero {}: {}", e.terceroId(), ex.getMessage());
        }
    }
}
