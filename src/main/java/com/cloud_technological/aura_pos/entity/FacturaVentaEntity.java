package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Factura de venta de Facturación (fuera del POS). En BORRADOR solo existe
 * aquí: no consume consecutivo, ni stock, ni asiento. Al emitirse se crea su
 * venta (venta.tipo_documento = 'FACTURA') y esta fila queda con los datos de
 * ERP (condición, orden de compra, vendedor) y el enlace {@link #ventaId}.
 */
@Entity
@Table(name = "factura_venta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FacturaVentaEntity {

    public static final String BORRADOR = "BORRADOR";
    public static final String EMITIDA = "EMITIDA";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "sucursal_id", nullable = false)
    private Integer sucursalId;

    @Column(name = "bodega_id")
    private Long bodegaId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "vendedor_id")
    private Integer vendedorId;

    @Column(name = "condicion_pago_id")
    private Long condicionPagoId;

    /** CREDITO | CONTADO */
    @Column(name = "forma_pago", nullable = false, length = 10)
    @Builder.Default
    private String formaPago = "CREDITO";

    /** Contado: EFECTIVO (caja general) o TRANSFERENCIA/CONSIGNACION/TARJETA (banco). */
    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "orden_compra", length = 60)
    private String ordenCompra;

    @Column(length = 1000)
    private String notas;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String estado = BORRADOR;

    @Column(name = "venta_id")
    private Long ventaId;

    /** Cotización de la que sale (la deja PARCIAL o CONVERTIDA al emitir). */
    @Column(name = "cotizacion_id")
    private Long cotizacionId;

    /** Pedido de vendedor que factura (queda enlazado y despachado al emitir). */
    @Column(name = "pedido_vendedor_id")
    private Long pedidoVendedorId;

    /** Centro de costo del asiento; null = el de la sucursal. */
    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    /** Contrato AIU (construcción): las líneas de la obra son costo directo sin IVA. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean aiu = false;

    @Column(name = "aiu_administracion_pct", nullable = false)
    @Builder.Default
    private BigDecimal aiuAdministracionPct = BigDecimal.ZERO;

    @Column(name = "aiu_imprevistos_pct", nullable = false)
    @Builder.Default
    private BigDecimal aiuImprevistosPct = BigDecimal.ZERO;

    @Column(name = "aiu_utilidad_pct", nullable = false)
    @Builder.Default
    private BigDecimal aiuUtilidadPct = BigDecimal.ZERO;

    /** IVA que se liquida sobre la Utilidad. */
    @Column(name = "aiu_iva_pct", nullable = false)
    @Builder.Default
    private BigDecimal aiuIvaPct = BigDecimal.valueOf(19);

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "descuento_total", nullable = false)
    @Builder.Default
    private BigDecimal descuentoTotal = BigDecimal.ZERO;

    @Column(name = "impuestos_total", nullable = false)
    @Builder.Default
    private BigDecimal impuestosTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "emitida_por")
    private Integer emitidaPor;

    @Column(name = "emitida_at")
    private LocalDateTime emitidaAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
