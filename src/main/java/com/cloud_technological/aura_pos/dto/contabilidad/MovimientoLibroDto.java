package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento del libro sobre la cuenta contable de un banco (E9): columna
 * "libro" de la pantalla de conciliación y candidato del matching sugerido.
 */
public record MovimientoLibroDto(
        Long asientoDetalleId,
        LocalDate fecha,
        String numeroComprobante,
        String descripcion,
        String tipoOrigen,
        BigDecimal debito,
        BigDecimal credito,
        boolean conciliado) {
}
