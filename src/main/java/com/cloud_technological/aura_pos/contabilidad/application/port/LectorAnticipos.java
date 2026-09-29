package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyección de anticipos y sus cruces para contabilizarlos (E6). */
public interface LectorAnticipos {

    AnticipoContable cargar(Long anticipoId, Integer empresaId);

    CruceContable cargarCruce(Long cruceId, Integer empresaId);

    /** @param tipo CLIENTE | PROVEEDOR */
    record AnticipoContable(
            String tipo,
            LocalDate fecha,
            BigDecimal monto,
            Long terceroId,
            String metodoPago,
            Long cuentaBancariaId,
            Long cuentaContableId) {

        public AnticipoContable(String tipo, LocalDate fecha, BigDecimal monto, Long terceroId,
                String metodoPago, Long cuentaBancariaId) {
            this(tipo, fecha, monto, terceroId, metodoPago, cuentaBancariaId, null);
        }
    }

    record CruceContable(
            String tipo,
            LocalDate fecha,
            BigDecimal monto,
            Long terceroId) {
    }
}
