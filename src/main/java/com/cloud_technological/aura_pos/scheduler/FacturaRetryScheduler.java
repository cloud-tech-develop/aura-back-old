package com.cloud_technological.aura_pos.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.implementations.FacturaRetryService;
import com.cloud_technological.aura_pos.sync.SyncProperties;

/**
 * Job automático que escanea periódicamente los logs de factura en estado PENDIENTE
 * que tengan un payload de reintento y los reejecutar automáticamente.
 *
 * Por defecto corre cada 5 minutos. Configurable via properties:
 *   app.facturacion.retry.cron=0 *\/5 * * * *
 */
@Component
public class FacturaRetryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FacturaRetryScheduler.class);

    private final FacturaRetryService facturaRetryService;

    @Autowired
    private SyncProperties syncProperties;

    @Autowired
    public FacturaRetryScheduler(FacturaRetryService facturaRetryService) {
        this.facturaRetryService = facturaRetryService;
    }

    @Scheduled(cron = "${app.facturacion.retry.cron:0 */5 * * * *}")
    public void ejecutarReintentos() {
        // En un servidor local (modo híbrido) solo la nube habla con Factus.
        if (syncProperties != null && syncProperties.esNodoLocal()) return;
        logger.info(">>> RETRY SCHEDULER: Iniciando escaneo de facturas PENDIENTES...");
        try {
            facturaRetryService.reintentarTodosPendientes();
        } catch (Exception e) {
            logger.error(">>> RETRY SCHEDULER: Error durante el escaneo de reintentos: {}", e.getMessage(), e);
        }
    }
}
