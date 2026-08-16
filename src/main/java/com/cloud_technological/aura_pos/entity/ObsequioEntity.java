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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Entrega de producto sin contraprestación (muestra, promoción, cortesía).
 * No genera ingreso ni cartera: solo saca inventario contra un gasto de
 * ventas, y opcionalmente causa el IVA por retiro de inventario.
 */
@Getter
@Setter
@Entity
@Table(name = "obsequio")
public class ObsequioEntity {

    public static final String ESTADO_APROBADO = "APROBADO";
    public static final String ESTADO_ANULADO = "ANULADO";

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
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    /** Quién recibió el obsequio. Null si no se identificó (muestra en punto de venta). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id")
    private TerceroEntity tercero;

    private LocalDateTime fecha;

    /** MUESTRA_COMERCIAL | PROMOCION | CORTESIA_CLIENTE | DONACION | OTRO */
    @Column(length = 30)
    private String motivo;

    @Column(length = 300)
    private String observacion;

    /** Costo de lo entregado: al gasto y fuera del inventario. */
    @Column(name = "costo_total", precision = 15, scale = 2)
    private BigDecimal costoTotal;

    /** Valor comercial sin IVA, base del impuesto por retiro. */
    @Column(name = "base_comercial_total", precision = 15, scale = 2)
    private BigDecimal baseComercialTotal;

    @Column(name = "iva_total", precision = 15, scale = 2)
    private BigDecimal ivaTotal;

    @Column(name = "genera_iva")
    private Boolean generaIva;

    @Column(length = 20)
    private String estado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (fecha == null) {
            fecha = LocalDateTime.now();
        }
    }
}
