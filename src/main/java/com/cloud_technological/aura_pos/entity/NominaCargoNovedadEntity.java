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
 * Novedad que se le puede reportar a quien tenga el cargo, con sus topes (V231).
 */
@Entity
@Table(name = "nomina_cargo_novedad")
@Getter
@Setter
public class NominaCargoNovedadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cargo_id", nullable = false)
    private Long cargoId;

    @Column(name = "concepto_numero", nullable = false)
    private Integer conceptoNumero;

    @Column(name = "tope_cantidad", precision = 10, scale = 2)
    private BigDecimal topeCantidad;

    @Column(name = "tope_valor", precision = 18, scale = 2)
    private BigDecimal topeValor;

    @Column(name = "requiere_autorizacion", nullable = false)
    private Boolean requiereAutorizacion = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
