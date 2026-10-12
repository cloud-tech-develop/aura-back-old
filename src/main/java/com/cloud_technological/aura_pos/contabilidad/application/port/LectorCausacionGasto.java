package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Proyección de una causación de gasto (V201) para contabilizarla. */
public interface LectorCausacionGasto {

    CausacionGastoContable cargar(Long causacionId, Integer empresaId);

    /** @param cuentaPagarId cuenta por pagar elegida; null → concepto PROVEEDORES */
    record CausacionGastoContable(
            String numero,
            LocalDate fecha,
            Long terceroId,
            String terceroNombre,
            String numeroSoporte,
            Long cuentaPagarId,
            BigDecimal totalPagar,
            List<LineaContable> lineas) {
    }

    /** @param retefuenteCuentaId cuenta del concepto de retención; null → RETEFUENTE_PRACTICADA */
    record LineaContable(
            Long cuentaId,
            String descripcion,
            Long centroCostoId,
            BigDecimal base,
            BigDecimal iva,
            boolean ivaDescontable,
            BigDecimal retefuente,
            Long retefuenteCuentaId,
            BigDecimal reteiva,
            BigDecimal reteica) {
    }
}
