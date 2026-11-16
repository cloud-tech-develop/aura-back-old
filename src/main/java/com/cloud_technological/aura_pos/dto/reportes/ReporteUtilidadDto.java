package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Reporte de utilidad en ventas: totales del período y filas agrupadas.
 *
 * <p>Cuenta contado y crédito (la utilidad nace al vender, no al cobrar). La parte a
 * crédito y lo que aún se debe de esas ventas salen aparte, como información.
 */
@Getter
@Setter
public class ReporteUtilidadDto {
    private Long ventas;
    private BigDecimal ventaNeta;
    private BigDecimal costo;
    private BigDecimal utilidad;
    private BigDecimal margenVenta;
    private BigDecimal margenCosto;

    /** Venta neta de las ventas hechas a crédito o con abono. */
    private BigDecimal ventaNetaCredito;
    private BigDecimal utilidadCredito;
    /** Lo que los clientes aún deben de las ventas del período. */
    private BigDecimal saldoPorCobrar;

    private Long lineasSinCosto;
    private Long lineasBajoCosto;

    private List<ReporteUtilidadFilaDto> filas = new ArrayList<>();
}
