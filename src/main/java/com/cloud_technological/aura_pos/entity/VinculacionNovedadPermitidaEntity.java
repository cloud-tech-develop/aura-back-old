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
 * Concepto que se le puede registrar como novedad a una vinculación, con tope
 * por período y si exige autorización (V212).
 */
@Entity
@Table(name = "vinculacion_novedad_permitida")
@Getter
@Setter
public class VinculacionNovedadPermitidaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "concepto_codigo", nullable = false, length = 30)
    private String conceptoCodigo;

    /** Máximo de unidades (horas, días…) por período; null = sin tope. */
    @Column(name = "tope_cantidad", precision = 10, scale = 2)
    private BigDecimal topeCantidad;

    /** Máximo en pesos por período; null = sin tope. */
    @Column(name = "tope_valor", precision = 18, scale = 2)
    private BigDecimal topeValor;

    @Column(name = "requiere_autorizacion", nullable = false)
    private Boolean requiereAutorizacion = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
