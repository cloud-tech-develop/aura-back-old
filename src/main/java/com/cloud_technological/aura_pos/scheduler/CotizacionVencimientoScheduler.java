package com.cloud_technological.aura_pos.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.CotizacionService;

@Component
public class CotizacionVencimientoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CotizacionVencimientoScheduler.class);

    private final CotizacionService cotizacionService;

    @Autowired
    public CotizacionVencimientoScheduler(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    // Corre todos los días a las 00:05
    @Scheduled(cron = "0 5 0 * * *")
    public void vencerCotizacionesExpiradas() {
        logger.info(">>> COTIZACION SCHEDULER: Verificando cotizaciones vencidas...");
        try {
            cotizacionService.vencerCotizacionesExpiradas();
            logger.info(">>> COTIZACION SCHEDULER: Proceso completado.");
        } catch (Exception e) {
            logger.error(">>> COTIZACION SCHEDULER: Error al vencer cotizaciones: {}", e.getMessage(), e);
        }
    }
}
