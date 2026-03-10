package com.cloud_technological.aura_pos.event;

import java.util.Map;

/**
 * Evento para registrar logs de factura de forma asíncrona tras el commit.
 */
public record FacturaLogEvent(
    Long facturaId,
    String evento,
    String estadoAnterior,
    String estadoNuevo,
    Map<String, Object> datos,
    Integer usuarioId,
    String mensaje,
    Map<String, Object> metadata
) {}
