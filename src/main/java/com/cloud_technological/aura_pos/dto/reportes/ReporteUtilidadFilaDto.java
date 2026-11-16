package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Una fila del reporte de utilidad en ventas: un producto, una categoría, un
 * vendedor, un cliente o un mes, según la agrupación pedida.
 */
@Getter
@Setter
public class ReporteUtilidadFilaDto {
    /** Identificador del grupo (id del producto, del cliente… o "2026-11" en el mes). */
    private String clave;
    private String nombre;
    /** Dato de apoyo: el SKU del producto o la identificación del cliente. */
    private String detalle;
    private Long ventas;
    private BigDecimal cantidad;
    /** Venta sin IVA, después de descuentos. */
    private BigDecimal ventaNeta;
    /** Costo congelado al vender (venta_detalle.costo_linea). */
    private BigDecimal costo;
    /** ventaNeta − costo. */
    private BigDecimal utilidad;
    /** Utilidad / venta neta × 100. */
    private BigDecimal margenVenta;
    /** Utilidad / costo × 100: el "le gano el X %" del comerciante. */
    private BigDecimal margenCosto;
    /** Líneas de productos inventariables vendidas sin costo: utilidad inflada. */
    private Long lineasSinCosto;
    /** Líneas vendidas por debajo del costo. */
    private Long lineasBajoCosto;
}
