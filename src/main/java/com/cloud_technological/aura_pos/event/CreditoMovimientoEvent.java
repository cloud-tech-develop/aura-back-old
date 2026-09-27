package com.cloud_technological.aura_pos.event;

/** Algo cambió en la cartera del cliente: toca revisar su score y sus reglas. */
public record CreditoMovimientoEvent(Long terceroId, Integer empresaId, String evento) {
}
