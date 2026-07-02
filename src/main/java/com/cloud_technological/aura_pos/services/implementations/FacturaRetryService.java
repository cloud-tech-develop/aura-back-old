package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.FacturaLogEntity;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaJPARepository;
import com.cloud_technological.aura_pos.repositories.factura_log.FacturaLogJPARepository;
import com.cloud_technological.aura_pos.services.FacturaService;

/**
 * Servicio responsable de leer los logs pendientes y reejecutar el flujo
 * correspondiente según el action almacenado en el campo metadata.
 */
@Service
public class FacturaRetryService {

    private static final Logger logger = LoggerFactory.getLogger(FacturaRetryService.class);

    private final FacturaLogJPARepository facturaLogRepository;
    private final FacturaJPARepository facturaJPARepository;
    private final FacturaService facturaService;

    @Autowired
    public FacturaRetryService(FacturaLogJPARepository facturaLogRepository,
                               FacturaJPARepository facturaJPARepository,
                               FacturaService facturaService) {
        this.facturaLogRepository = facturaLogRepository;
        this.facturaJPARepository = facturaJPARepository;
        this.facturaService = facturaService;
    }

    /**
     * Intenta reintentar un flujo de factura específico usando el último log
     * en estado PENDIENTE que tenga metadata con action de reintento.
     *
     * @param facturaId ID de la factura a reintentar
     * @return true si se encontró y ejecutó un payload de reintento, false si no había nada
     */
    public boolean reintentar(Long facturaId) {
        List<FacturaLogEntity> logs = facturaLogRepository.findByFacturaIdOrderByCreatedAtDesc(facturaId);

        FacturaLogEntity pendingLog = logs.stream()
            .filter(l -> "PENDIENTE".equals(l.getEstadoNuevo()))
            .filter(l -> l.getMetadata() != null)
            .findFirst()
            .orElse(null);

        if (pendingLog == null) {
            logger.warn("No se encontró log PENDIENTE con payload de reintento para factura {}", facturaId);
            return false;
        }

        return ejecutarPayload(pendingLog);
    }

    /**
     * Escanea todos los logs PENDIENTES con metadata de reintento y los ejecuta.
     * Utilizado por el scheduler automático.
     */
    public void reintentarTodosPendientes() {
        List<FacturaLogEntity> pendingLogs = facturaLogRepository.findPendingWithRetryPayload();
        logger.info(">>> RETRY SCHEDULER: Encontrados {} logs PENDIENTES con payload de reintento", pendingLogs.size());

        for (FacturaLogEntity log : pendingLogs) {
            try {
                ejecutarPayload(log);
            } catch (Exception e) {
                logger.error("Error al reintentar factura {} - {}", log.getFacturaId(), e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean ejecutarPayload(FacturaLogEntity log) {
        Map<String, Object> metadata = (Map<String, Object>) log.getMetadata();
        String action = (String) metadata.get("action");

        if (action == null) {
            logger.warn("El log {} no tiene 'action' en metadata", log.getId());
            return false;
        }

        logger.info(">>> Ejecutando reintento para factura {} - action: {}", log.getFacturaId(), action);

        switch (action) {
            case "crearDesdeVenta" -> {
                Long ventaId = toLong(metadata.get("ventaId"));
                Integer empresaId = toInteger(metadata.get("empresaId"));
                Integer usuarioId = toInteger(metadata.get("usuarioId"));

                // Idempotencia: si la factura ya existe para esta venta, el trabajo ya
                // está hecho. Reintentar solo produciría "Ya existe una factura para esta
                // venta". Se resuelve el log (se limpia el payload) para que el scheduler
                // no lo vuelva a seleccionar.
                if (facturaJPARepository.findByVentaId(ventaId).isPresent()) {
                    logger.info(">>> Factura de venta {} ya existe - nada que reintentar, se resuelve log {}",
                        ventaId, log.getId());
                    resolverLog(log);
                    return true;
                }

                facturaService.crearDesdeVenta(ventaId, empresaId, usuarioId);
                logger.info(">>> Reintento exitoso para factura {} - action: crearDesdeVenta", log.getFacturaId());
            }
            default -> {
                logger.warn("Action desconocido '{}' en log {} - se omite", action, log.getId());
                return false;
            }
        }

        return true;
    }

    /**
     * Marca un log como resuelto limpiando su payload de reintento (metadata),
     * de modo que {@code findPendingWithRetryPayload} deje de seleccionarlo. Se
     * conserva el resto de la información de auditoría (estado, datos, mensaje).
     */
    private void resolverLog(FacturaLogEntity log) {
        log.setMetadata(null);
        facturaLogRepository.save(log);
    }

    private Long toLong(Object value) {
        if (value instanceof Number num) return num.longValue();
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number num) return num.intValue();
        return Integer.parseInt(value.toString());
    }
}
