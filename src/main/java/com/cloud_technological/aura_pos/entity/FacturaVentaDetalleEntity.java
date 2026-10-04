package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

/** Línea de la factura de Facturación: producto (o servicio) con su texto propio. */
@Entity
@Table(name = "factura_venta_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FacturaVentaDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factura_venta_id", nullable = false)
    private Long facturaVentaId;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "producto_presentacion_id")
    private Long productoPresentacionId;

    /** Null = el nombre del producto. */
    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal cantidad;

    /** Sin IVA, por unidad (o por presentación). */
    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "descuento_valor", nullable = false)
    @Builder.Default
    private BigDecimal descuentoValor = BigDecimal.ZERO;

    @Column(name = "impuesto_porcentaje", nullable = false)
    @Builder.Default
    private BigDecimal impuestoPorcentaje = BigDecimal.ZERO;

    @Column(name = "impuesto_valor", nullable = false)
    @Builder.Default
    private BigDecimal impuestoValor = BigDecimal.ZERO;

    /** Base neta + impuesto (igual que venta_detalle.subtotal_linea). */
    @Column(name = "subtotal_linea", nullable = false)
    @Builder.Default
    private BigDecimal subtotalLinea = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;

    /** ADMINISTRACION | IMPREVISTOS | UTILIDAD si la generó el AIU; null en las de la obra. */
    @Column(name = "aiu_tipo", length = 15)
    private String aiuTipo;

    /** Línea de la cotización que factura (para el pendiente por línea). */
    @Column(name = "cotizacion_detalle_id")
    private Long cotizacionDetalleId;
}
