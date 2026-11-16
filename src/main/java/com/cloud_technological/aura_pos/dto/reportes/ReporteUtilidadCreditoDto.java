package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Una venta a crédito (o con abono) del período, con su utilidad y lo que se ha
 * cobrado: el desglose de "Utilidad de ventas a crédito" del reporte de utilidad.
 */
@Getter
@Setter
public class ReporteUtilidadCreditoDto {
    private Long ventaId;
    /** Prefijo-consecutivo de la venta (el mismo número de las demás pantallas). */
    private String documento;
    /** Cuenta por cobrar que generó la venta (CC-…), si existe. */
    private String numeroCuenta;
    private LocalDateTime fecha;
    private String clienteNombre;
    private String clienteDocumento;
    /** Venta sin IVA, después de descuentos. */
    private BigDecimal ventaNeta;
    private BigDecimal costo;
    private BigDecimal utilidad;
    /** Total de la venta con IVA: lo que el cliente quedó debiendo más lo que pagó de contado. */
    private BigDecimal totalVenta;
    /** Lo abonado a la cuenta por cobrar. */
    private BigDecimal abonado;
    /** Lo que el cliente aún debe de esta venta. */
    private BigDecimal porCobrar;
    /** Estado de la cuenta por cobrar (activa, pagada, castigada…). */
    private String estadoCartera;
}
