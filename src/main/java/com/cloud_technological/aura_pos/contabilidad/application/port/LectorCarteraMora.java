package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyección de los cobros de intereses de mora y los castigos de cartera (C5/C9, V205). */
public interface LectorCarteraMora {

    InteresContable interes(Long interesId, Integer empresaId);

    CastigoContable castigo(Long castigoId, Integer empresaId);

    record InteresContable(String numero, LocalDate fecha, Long terceroId, String terceroNombre, BigDecimal valor) {
    }

    record CastigoContable(String numero, LocalDate fecha, Long terceroId, String numeroCuenta, BigDecimal valor,
            BigDecimal contraProvision, BigDecimal contraGasto) {
    }
}
