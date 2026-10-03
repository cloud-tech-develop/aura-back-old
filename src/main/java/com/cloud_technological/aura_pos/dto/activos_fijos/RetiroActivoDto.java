package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Baja o venta de un activo. En la venta, {@code valorVenta} es el precio sin
 * IVA y {@code cuentaCobroId} la cuenta que recibe (caja, banco o clientes).
 */
@Getter
@Setter
public class RetiroActivoDto {
    private LocalDate fecha;
    private String motivo;
    private BigDecimal valorVenta;
    /** Tarifa de IVA de la venta (0, 5, 19). */
    private BigDecimal ivaPorcentaje;
    private Long cuentaCobroId;
    private Long compradorTerceroId;
}
