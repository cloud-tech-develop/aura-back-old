package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CierreContableDto {

    private String fechaDesde;
    private String fechaHasta;

    // ── Ventas del período ─────────────────────────────────
    private Integer    cantidadVentas;
    private BigDecimal totalVentasBruto;       // subtotal con IVA (legacy: total_pagar)
    private BigDecimal totalDescuentos;
    private BigDecimal totalImpuestos;         // IVA cobrado
    private BigDecimal totalVentasNeto;        // total_pagar (legacy, con IVA)
    private BigDecimal totalVentasSinIva;      // subtotal − descuentos (base real)
    private BigDecimal ventasBrutasConDisponible; // totalVentasBruto + totalDisponible (caja/bancos)

    // ── Compras del período ────────────────────────────────
    private Integer    cantidadCompras;
    private BigDecimal totalComprasNeto;       // total con IVA (legacy)
    private BigDecimal totalComprasSinIva;     // subtotal − descuentos (base real)
    private BigDecimal totalIvaCompras;        // IVA pagado en compras

    // ── COGS (costo real de lo vendido) ────────────────────
    private BigDecimal costoVentas;            // Σ (cantidad × producto.costo)
    private Integer    productosSinCosto;      // items vendidos sin costo cargado
    private BigDecimal valorVentasSinCosto;    // monto vendido sin costo configurado

    // ── Comisiones del período ─────────────────────────────
    private Integer    cantidadComisiones;
    private BigDecimal totalComisionesTecnicos;

    // ── Mermas del período ─────────────────────────────────
    private Integer    cantidadMermas;
    private BigDecimal totalMermas;

    // ── Resultados (modelo P&L corregido sin IVA) ──────────
    private BigDecimal utilidadBruta;          // ventas sin IVA − COGS − mermas
    private BigDecimal utilidadOperativa;      // bruta − comisiones − gastos deducibles
    private BigDecimal utilidadNeta;           // operativa − gastos no deducibles
    private BigDecimal margenBruto;            // %
    private BigDecimal margenOperativo;        // %
    private BigDecimal margenNeto;             // %

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

    // ── Movimientos de caja del período (egresos POS internos) ──
    private BigDecimal totalIngresos;
    private BigDecimal totalEgresos;
    private Integer    cantidadIngresos;
    private Integer    cantidadEgresos;
    private List<MovimientoCierreDto> detalleMovimientos = new ArrayList<>();

    // ── Gastos del período ─────────────────────────────────
    private Integer    cantidadGastos;
    private BigDecimal totalGastosDeducibles;
    private BigDecimal totalGastosNoDeducibles;
    private BigDecimal totalGastos;

    // ── Posición de efectivo (saldos del mayor: caja/bancos 11xx a fechaHasta) ──
    private List<SaldoDisponibleDto> disponible = new ArrayList<>();
    private BigDecimal totalDisponible;
}
