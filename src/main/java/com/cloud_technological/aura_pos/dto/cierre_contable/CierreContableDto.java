package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CierreContableDto {

    private String fechaDesde;
    private String fechaHasta;

    // ── Ventas del período ─────────────────────────────────
    private Integer    cantidadVentas;
    private BigDecimal totalVentasBruto;
    private BigDecimal totalDescuentos;
    private BigDecimal totalImpuestos;
    private BigDecimal totalVentasNeto;

    // ── Compras del período ────────────────────────────────
    private Integer    cantidadCompras;
    private BigDecimal totalComprasNeto;

    // ── Comisiones del período ─────────────────────────────
    private Integer    cantidadComisiones;
    private BigDecimal totalComisionesTecnicos;

    // ── Resultados ─────────────────────────────────────────
    private BigDecimal utilidadBruta;      // ventas neto − compras
    private BigDecimal utilidadNeta;       // utilidad bruta − comisiones
    private BigDecimal margenBruto;        // %
    private BigDecimal margenNeto;         // %

    // ── Cuentas por cobrar (snapshot) ─────────────────────
    private BigDecimal cxcTotalDeuda;
    private BigDecimal cxcSaldoPendiente;
    private Integer    cxcCantidadActivas;
    private Integer    cxcCantidadVencidas;

    // ── Cuentas por pagar (snapshot) ──────────────────────
    private BigDecimal cxpTotalDeuda;
    private BigDecimal cxpSaldoPendiente;
    private Integer    cxpCantidadActivas;
    private Integer    cxpCantidadVencidas;

    // ── Posición neta (lo que te deben - lo que debes) ────
    private BigDecimal posicionNeta;
}
