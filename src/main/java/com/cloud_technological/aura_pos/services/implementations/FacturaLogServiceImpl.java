package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.entity.FacturaLogEntity;
import com.cloud_technological.aura_pos.event.FacturaLogEvent;
import com.cloud_technological.aura_pos.repositories.factura_log.FacturaLogJPARepository;
import com.cloud_technological.aura_pos.services.FacturaLogService;

@Service
public class FacturaLogServiceImpl implements FacturaLogService {

    private static final Logger logger = LoggerFactory.getLogger(FacturaLogServiceImpl.class);
    
    private final FacturaLogJPARepository facturaLogRepository;

    @Autowired
    public FacturaLogServiceImpl(FacturaLogJPARepository facturaLogRepository) {
        this.facturaLogRepository = facturaLogRepository;
    }

    @Override
    @Async("facturaLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarLogAsync(Long facturaId, String evento, String estadoAnterior, 
            String estadoNuevo, java.util.Map<String, Object> datos, Integer usuarioId, String mensaje, java.util.Map<String, Object> metadata) {
        try {
            logger.info(">>> INICIANDO REGISTRO DE LOG PARA FACTURA {} - EVENTO: {}", facturaId, evento);
            
            FacturaLogEntity log = new FacturaLogEntity();
            log.setFacturaId(facturaId);
            log.setEvento(evento);
            log.setEstadoAnterior(estadoAnterior);
            log.setEstadoNuevo(estadoNuevo);
            log.setDatos(datos);
            log.setUsuarioId(usuarioId);
            log.setMensaje(mensaje);
            log.setMetadata(metadata);
            log.setCreatedAt(LocalDateTime.now());

            FacturaLogEntity saved = facturaLogRepository.saveAndFlush(log);
            logger.info(">>> LOG GUARDADO EXITOSAMENTE - ID: {} - FACTURA: {} - EVENTO: {}", 
                saved.getId(), saved.getFacturaId(), saved.getEvento());
            
        } catch (Exception e) {
            // Silencioso - nunca debe fallar el proceso principal
            logger.error("Error al registrar log de factura: {} - {}", facturaId, e.getMessage(), e);
        }
    }

    @Async("facturaLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFacturaLogEvent(FacturaLogEvent event) {
        logger.info(">>> RECIBIDO EVENTO DE LOG PARA FACTURA: {}", event.facturaId());
        this.registrarLogAsync(
            event.facturaId(),
            event.evento(),
            event.estadoAnterior(),
            event.estadoNuevo(),
            event.datos(),
            event.usuarioId(),
            event.mensaje(),
            event.metadata()
        );
    }

    @Override
    public List<FacturaLogEntity> obtenerPorFactura(Long facturaId) {
        return facturaLogRepository.findByFacturaIdOrderByCreatedAtDesc(facturaId);
    }
}
