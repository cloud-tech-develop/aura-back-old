package com.cloud_technological.aura_pos.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.implementations.AcuerdoPagoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pasada nocturna: las cuotas que vencieron ayer dejan el acuerdo incumplido
 * aunque nadie abra la cartera. Corre antes de las reglas de crédito (00:30),
 * que así ya ven la mora por cuota.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcuerdoPagoScheduler {

    private final AcuerdoPagoService acuerdoPagoService;

    @Scheduled(cron = "0 20 0 * * *", zone = "America/Bogota")
    public void evaluarAcuerdos() {
        try {
            int cambios = acuerdoPagoService.evaluar(null, null, null);
            log.info(">>> ACUERDOS DE PAGO: {} acuerdos cambiaron de estado", cambios);
        } catch (Exception e) {
            log.error(">>> ACUERDOS DE PAGO: error evaluando acuerdos: {}", e.getMessage(), e);
        }
    }
}
