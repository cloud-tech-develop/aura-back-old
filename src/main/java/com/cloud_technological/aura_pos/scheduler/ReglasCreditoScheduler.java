package com.cloud_technological.aura_pos.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.implementations.ReglaCreditoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cada noche, después de resolver las promesas: score, suspensión por mora y
 * reglas PERIODICO de todos los clientes con crédito.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReglasCreditoScheduler {

    private final ReglaCreditoService reglaCreditoService;

    @Scheduled(cron = "0 30 0 * * *", zone = "America/Bogota")
    public void evaluar() {
        try {
            int aplicadas = reglaCreditoService.evaluarTodos();
            log.info(">>> REGLAS DE CRÉDITO: {} acciones aplicadas", aplicadas);
        } catch (Exception e) {
            log.error(">>> REGLAS DE CRÉDITO: error en la pasada nocturna: {}", e.getMessage(), e);
        }
    }
}
