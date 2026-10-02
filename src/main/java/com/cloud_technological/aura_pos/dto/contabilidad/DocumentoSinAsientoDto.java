package com.cloud_technological.aura_pos.dto.contabilidad;

import java.time.LocalDate;

/**
 * Documento operativo que no tiene exactamente un asiento vigente.
 *
 * @param vigentes 0 = falta en el mayor (se puede reprocesar); más de 1 = duplicado
 */
public record DocumentoSinAsientoDto(
        String tipoOrigen,
        Long origenId,
        LocalDate fecha,
        String numero,
        int vigentes) {
}
