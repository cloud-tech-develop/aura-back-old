package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.entity.FacturaLogEntity;

import org.springframework.scheduling.annotation.Async;

public interface FacturaLogService {
    
    @Async
    void registrarLogAsync(Long facturaId, String evento, String estadoAnterior, 
            String estadoNuevo, java.util.Map<String, Object> datos, Integer usuarioId, String mensaje, java.util.Map<String, Object> metadata);
    
    List<FacturaLogEntity> obtenerPorFactura(Long facturaId);
}
