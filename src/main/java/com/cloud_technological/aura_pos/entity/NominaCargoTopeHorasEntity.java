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
 * Máximo de horas extra o recargos de un tipo para el cargo (V231). El tipo es una
 * fila 3–9 de {@code nomina_concepto_tipo}.
 */
@Entity
@Table(name = "nomina_cargo_tope_horas")
@Getter
@Setter
public class NominaCargoTopeHorasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cargo_id", nullable = false)
    private Long cargoId;

    @Column(name = "tipo_concepto_id", nullable = false)
    private Integer tipoConceptoId;

    @Column(name = "tope_dia", precision = 5, scale = 2)
    private BigDecimal topeDia;

    @Column(name = "tope_semana", precision = 5, scale = 2)
    private BigDecimal topeSemana;

    @Column(name = "tope_mes", precision = 6, scale = 2)
    private BigDecimal topeMes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
