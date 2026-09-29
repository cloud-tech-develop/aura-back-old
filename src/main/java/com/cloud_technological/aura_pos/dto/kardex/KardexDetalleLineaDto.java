package com.cloud_technological.aura_pos.dto.kardex;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Una línea del kardex clásico: los movimientos de un producto en orden
 * cronológico, con su saldo corrido. Es la vista que pide un contador.
 */
@Getter
@Setter
public class KardexDetalleLineaDto {

    private Long id;
    private LocalDateTime fecha;
    private String tipoMovimiento;
    /** La etiqueta legible del tipo; el código crudo si no está en el catálogo. */
    private String tipoEtiqueta;
    private String grupo;
    private String referenciaOrigen;

    private String sucursalNombre;
    private String codigoLote;

    /**
     * La variación real: {@code saldo_nuevo - saldo_anterior}.
     *
     * <p>No se usa la columna {@code cantidad} a propósito. Casi todos los
     * orígenes guardan la salida negada, pero el reconteo guarda el valor
     * absoluto y pone el sentido en el nombre del tipo. Sumar {@code cantidad}
     * a ciegas da mal el reporte en cuanto hay un reconteo de por medio.
     */
    private BigDecimal movimiento;

    private BigDecimal saldoAnterior;
    private BigDecimal saldoNuevo;
    private BigDecimal costoHistorico;
    /** movimiento × costo histórico, en valor absoluto. */
    private BigDecimal valorMovimiento;

    private long totalRows;
}
