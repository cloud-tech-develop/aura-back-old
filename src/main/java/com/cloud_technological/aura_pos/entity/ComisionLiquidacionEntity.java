package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "comision_liquidacion")
@Getter
@Setter
public class ComisionLiquidacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private UsuarioEntity tecnico;

    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(name = "total_servicios")
    private Integer totalServicios;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    private String estado = "PENDIENTE"; // PENDIENTE | PAGADA

    private String observaciones;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "metodo_pago")
    private String metodoPago;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
