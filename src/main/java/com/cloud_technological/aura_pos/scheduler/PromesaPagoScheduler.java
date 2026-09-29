package com.cloud_technological.aura_pos.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.implementations.PromesaPagoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pasada nocturna: las promesas cuya fecha ya pasó quedan incumplidas aunque
 * nadie abra la cartera. Hora de Colombia: en producción la JVM corre en UTC.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromesaPagoScheduler {

    private final PromesaPagoService promesaPagoService;

    @Scheduled(cron = "0 15 0 * * *", zone = "America/Bogota")
    public void evaluarPromesas() {
        try {
            int cambios = promesaPagoService.evaluar(null, null);
            log.info(">>> PROMESAS DE PAGO: {} promesas actualizadas", cambios);
        } catch (Exception e) {
            log.error(">>> PROMESAS DE PAGO: error evaluando promesas: {}", e.getMessage(), e);
        }
    }
}
