package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

/**
 * Movimiento acumulado de una cuenta en un período (E10 · flujo de efectivo):
 * insumo para clasificar variaciones por grupo PUC entre dos cortes.
 */
public record MovimientoCuentaDto(
        String codigo,
        String nombre,
        String tipo,
        BigDecimal debito,
        BigDecimal credito) {
}
