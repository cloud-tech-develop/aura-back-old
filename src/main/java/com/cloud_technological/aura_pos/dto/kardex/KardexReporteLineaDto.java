package com.cloud_technological.aura_pos.dto.kardex;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Una fila del reporte de movimiento de inventario: un producto (y, según la
 * agrupación, su sucursal o su lote) con lo que entró y salió en el rango.
 *
 * <p>Los saldos <b>se leen, no se calculan</b>: el inicial es el
 * {@code saldo_anterior} del primer movimiento del rango y el final el
 * {@code saldo_nuevo} del último. Recalcularlos sumando cantidades daría un
 * número distinto en cuanto una fila esté mal grabada, y ese descuadre es justo
 * lo que el reporte tiene que dejar ver.
 */
@Getter
@Setter
public class KardexReporteLineaDto {

    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private String categoriaNombre;
    private String marcaNombre;

    /** Solo en las agrupaciones que los separan; null en las demás. */
    private Long sucursalId;
    private String sucursalNombre;
    private Long loteId;
    private String codigoLote;

    private BigDecimal saldoInicial;
    private BigDecimal saldoFinal;

    /** Suma de las variaciones positivas, en unidades. Siempre >= 0. */
    private BigDecimal entradas;
    /** Suma de las variaciones negativas, en unidades positivas. Siempre >= 0. */
    private BigDecimal salidas;
    /** entradas − salidas. Debe coincidir con saldoFinal − saldoInicial. */
    private BigDecimal variacionNeta;

    /** Valorización al costo histórico de cada movimiento. */
    private BigDecimal valorEntradas;
    private BigDecimal valorSalidas;

    private Integer cantidadMovimientos;

    // ── Desglose por tipo, en unidades ───────────────────────────────────
    private BigDecimal compras;
    private BigDecimal ventas;
    private BigDecimal devoluciones;
    private BigDecimal mermas;
    private BigDecimal obsequios;
    private BigDecimal traslados;
    private BigDecimal anulaciones;
    private BigDecimal reconteos;
    private BigDecimal otros;

    private long totalRows;
}
