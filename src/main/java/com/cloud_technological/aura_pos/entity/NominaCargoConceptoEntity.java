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

/**
 * Concepto que el cargo liquida siempre (V231). Lo heredan las vinculaciones del
 * cargo. El concepto se referencia por su número visible.
 */
@Entity
@Table(name = "nomina_cargo_concepto")
@Getter
@Setter
public class NominaCargoConceptoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cargo_id", nullable = false)
    private Long cargoId;

    @Column(name = "concepto_numero", nullable = false)
    private Integer conceptoNumero;

    @Column(precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(precision = 7, scale = 4)
    private BigDecimal porcentaje;

    @Column(length = 300)
    private String observacion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
