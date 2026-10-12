package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Concepto de nómina propio de una vinculación (bonificación fija, auxilio de
 * rodamiento…) con su valor y vigencia (V212). El concepto va por código porque
 * {@code concepto_nomina} guarda versiones por vigencia.
 */
@Entity
@Table(name = "vinculacion_concepto")
@Getter
@Setter
public class VinculacionConceptoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "concepto_codigo", nullable = false, length = 30)
    private String conceptoCodigo;

    @Column(precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(precision = 7, scale = 4)
    private BigDecimal porcentaje;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(length = 300)
    private String observacion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
