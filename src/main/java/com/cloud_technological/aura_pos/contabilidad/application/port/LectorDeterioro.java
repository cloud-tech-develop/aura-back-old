package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyección de una propuesta de deterioro de cartera (E6). */
public interface LectorDeterioro {

    DeterioroContable cargar(Long calculoId, Integer empresaId);

    record DeterioroContable(LocalDate fecha, BigDecimal monto, String detalle) {
    }
}
