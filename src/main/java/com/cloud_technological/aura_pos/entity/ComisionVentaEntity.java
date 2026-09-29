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

@Entity
@Table(name = "comision_venta")
@Getter
@Setter
public class ComisionVentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private VentaEntity venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_detalle_id")
    private VentaDetalleEntity ventaDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    // tecnico_id → usuario de la empresa
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private UsuarioEntity tecnico;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    @Column(name = "porcentaje_tecnico")
    private BigDecimal porcentajeTecnico;

    @Column(name = "porcentaje_negocio")
    private BigDecimal porcentajeNegocio;

    @Column(name = "valor_tecnico")
    private BigDecimal valorTecnico;

    @Column(name = "valor_negocio")
    private BigDecimal valorNegocio;

    // SERVICIO = comisión de técnico | VENTA = comisión de vendedor
    private String modalidad = "SERVICIO";

    // Solo para VENTA: empleado que hizo la venta
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id")
    private EmpleadoEntity vendedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "liquidacion_id")
    private ComisionLiquidacionEntity liquidacion; // NULL = pendiente

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
