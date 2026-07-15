package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyecciones de las operaciones del cierre anual (E8) para sus asientos. */
public interface LectorCierreAnual {

    OperacionContable cargarOperacion(Long id, Integer empresaId);

    DistribucionContable cargarDistribucion(Long id, Integer empresaId);

    PagoDividendoContable cargarPago(Long id, Integer empresaId);

    /** PROVISION_RENTA (monto > 0) o TRASLADO (monto con signo: <0 pérdida). */
    record OperacionContable(String tipo, Integer anio, LocalDate fecha,
            BigDecimal monto, String detalle) {
    }

    record DistribucionContable(Integer anio, LocalDate fecha,
            BigDecimal reservaLegal, BigDecimal dividendos) {
    }

    record PagoDividendoContable(LocalDate fecha, BigDecimal monto,
            String metodoPago, Long cuentaBancariaId, Long terceroId) {
    }
}
