package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "compra")
@Getter
@Setter
public class CompraEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private SucursalEntity sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private TerceroEntity proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @Column(name = "numero_compra")
    private String numeroCompra;

    private LocalDateTime fecha;

    private BigDecimal subtotal;

    @Column(name = "descuento_total")
    private BigDecimal descuentoTotal;

    @Column(name = "impuestos_total")
    private BigDecimal impuestosTotal;

    private BigDecimal total;
    private String observaciones;
    private String estado;

    @Column(name = "retefuente_pct")
    private BigDecimal retefuentePct;

    @Column(name = "retefuente_valor")
    private BigDecimal retefuenteValor;

    @Column(name = "reteiva_pct")
    private BigDecimal reteivaPct;

    @Column(name = "reteiva_valor")
    private BigDecimal reteivaValor;

    @Column(name = "reteica_pct")
    private BigDecimal reteicaPct;

    @Column(name = "reteica_valor")
    private BigDecimal reteicaValor;

    @Column(name = "total_retenciones")
    private BigDecimal totalRetenciones;

    @Column(name = "neto_a_pagar")
    private BigDecimal netaAPagar;

    @Column(name = "forma_pago", length = 20)
    private String formaPago;

    @Column(name = "tipo_documento", length = 30)
    private String tipoDocumento;

    @Column(name = "fletes")
    private BigDecimal fletes;

    // ── Nota crédito de compra ───────────────────────────────────────────
    /**
     * Factura de compra que esta nota crédito corrige. Solo aplica cuando
     * {@link #tipoDocumento} es {@code NOTA_CREDITO}: sin ella no se puede
     * validar que no se acredite más de lo comprado ni saber contra qué cuenta
     * por pagar cruzarla.
     */
    @Column(name = "compra_origen_id")
    private Long compraOrigenId;

    /**
     * Qué pasa con la plata de la nota crédito:
     * {@code CRUCE_CXP} baja la deuda de la factura origen,
     * {@code DEVOLUCION_DINERO} la devuelve el proveedor a caja/banco y
     * {@code SALDO_A_FAVOR} la deja como crédito para compras futuras.
     */
    @Column(name = "destino_nota_credito", length = 20)
    private String destinoNotaCredito;

    // ── Destino contable (E2 · pieza 4) ─────────────────────────────────
    /** Centro de costo que se propaga a todas las líneas del asiento. */
    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    /** Cuenta débito de la compra (gasto/activo); null → inventario. */
    @Column(name = "cuenta_contable_id")
    private Long cuentaContableId;

    // ── Dimensiones proyecto/frente (E7) ─────────────────────────────────
    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "frente_id")
    private Long frenteId;

    /**
     * Por qué una compra de fecha anterior se cargó a la caja de hoy, y quién lo
     * autorizó. Sin esto la autorización no sirve: el objetivo no es solo
     * frenar, es poder preguntar después qué pasó ese día.
     */
    @Column(name = "motivo_retroactivo", length = 500)
    private String motivoRetroactivo;

    @Column(name = "autorizado_por")
    private Integer autorizadoPor;

    /**
     * La plata ya había salido del cajón otro día cuando se registró este
     * documento. No genera movimiento de caja — ni en la de hoy ni en la de
     * aquel día, que ya cerró cuadrada contra el conteo físico.
     */
    @Column(name = "salida_caja_otro_dia", nullable = false)
    private Boolean salidaCajaOtroDia = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
