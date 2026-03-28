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

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
