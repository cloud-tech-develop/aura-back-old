package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Proyección de una ejecución de causación programada (E6). */
public interface LectorCausacion {

    CausacionContable cargar(Long ejecucionId, Integer empresaId);

    record CausacionContable(
            String nombre,
            String periodo,
            LocalDate fecha,
            List<LineaCausacion> lineas) {
    }

    record LineaCausacion(
            Long cuentaId,
            String descripcion,
            BigDecimal debito,
            BigDecimal credito,
            Long terceroId) {
    }
}
