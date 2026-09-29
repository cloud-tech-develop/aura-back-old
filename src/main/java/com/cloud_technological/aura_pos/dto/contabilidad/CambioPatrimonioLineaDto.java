package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

/**
 * Línea del estado de cambios en el patrimonio (E10): una cuenta clase 3 con
 * su saldo inicial, aumentos (créditos), disminuciones (débitos) y saldo
 * final del período. Saldos en naturaleza crédito (positivo = patrimonio).
 */
public record CambioPatrimonioLineaDto(
        String codigo,
        String nombre,
        BigDecimal saldoInicial,
        BigDecimal aumentos,
        BigDecimal disminuciones,
        BigDecimal saldoFinal) {
}
