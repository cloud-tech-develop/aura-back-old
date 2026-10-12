package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Proyección del pago de una declaración (V204) para contabilizarlo. */
public interface LectorDeclaracionPago {

    PagoDeclaracion cargar(Long pagoId, Integer empresaId);

    record PagoDeclaracion(
            String numero,
            String tipo,
            LocalDate desde,
            LocalDate hasta,
            LocalDate fecha,
            Long terceroAcreedorId,
            String terceroNombre,
            String numeroFormulario,
            BigDecimal valor,
            String metodoPago,
            Long cuentaBancariaId,
            Long cuentaContableId,
            /** Redondeo a miles: &gt; 0 se pagó de más (gasto), &lt; 0 de menos (ingreso). */
            BigDecimal ajusteRedondeo,
            BigDecimal intereses,
            BigDecimal sancion,
            List<Cierre> cierres) {
    }

    /** Lo que cierra de cada cuenta: valor &gt; 0 se debita, &lt; 0 se acredita. */
    record Cierre(Long cuentaId, BigDecimal valor) {
    }
}
