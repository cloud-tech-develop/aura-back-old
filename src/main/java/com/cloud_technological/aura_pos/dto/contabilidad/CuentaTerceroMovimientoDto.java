package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

/**
 * Movimiento acumulado por cuenta × tercero (E11 · exógena): insumo de la
 * generación del lote — cada fila se asigna al mapeo más específico del
 * formato y se agrupa por tercero × concepto.
 */
public record CuentaTerceroMovimientoDto(
        String codigo,
        Long terceroId,
        BigDecimal debito,
        BigDecimal credito) {
}
