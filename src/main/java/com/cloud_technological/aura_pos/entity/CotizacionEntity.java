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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cotizacion")
@Getter
@Setter
public class CotizacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id")
    private TerceroEntity tercero;

    @Column(name = "turno_caja_id")
    private Long turnoCajaId;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "subtotal", precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "iva", precision = 14, scale = 2)
    private BigDecimal iva;

    @Column(name = "descuento", precision = 14, scale = 2)
    private BigDecimal descuento;

    @Column(name = "total", precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "estado", length = 20)
    private String estado;

    @Column(name = "dias_vigencia")
    private Integer diasVigencia = 3;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
