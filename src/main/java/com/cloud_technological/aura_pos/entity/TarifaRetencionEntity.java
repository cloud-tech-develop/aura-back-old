package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tarifa_retencion")
@Getter
@Setter
public class TarifaRetencionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** RETEFUENTE | RETEIVA | RETEICA */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 100)
    private String concepto;

    @Column(name = "codigo_concepto", length = 20)
    private String codigoConcepto;

    @Column(name = "tarifa_natural", nullable = false, precision = 5, scale = 2)
    private BigDecimal tarifaNatural;

    @Column(name = "tarifa_juridica", nullable = false, precision = 5, scale = 2)
    private BigDecimal tarifaJuridica;

    @Column(name = "base_minima", nullable = false, precision = 18, scale = 2)
    private BigDecimal baseMinima = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
