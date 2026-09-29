package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyección de una línea de extracto marcada como ajuste (E9) para su
 * generador: qué cobró/abonó el banco y contra qué cuenta contable del
 * banco se registra.
 */
public interface LectorAjusteBancario {

    AjusteBancario cargar(Long lineaId, Integer empresaId);

    /** valor con el signo del banco: &gt;0 abono / &lt;0 cargo. */
    record AjusteBancario(
            LocalDate fecha,
            String descripcion,
            BigDecimal valor,
            String tipoAjuste,
            Long cuentaContableBancoId,
            Long terceroBancoId) {
    }
}
