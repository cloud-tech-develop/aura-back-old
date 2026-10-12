package com.cloud_technological.aura_pos.entity;

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
 * Retiro de una vinculación (V219): termina el contrato en la fecha de retiro y
 * queda pendiente de su liquidación definitiva ({@code documentoId}).
 */
@Entity
@Table(name = "nomina_retiro")
@Getter
@Setter
public class NominaRetiroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    @Column(name = "fecha_retiro", nullable = false)
    private LocalDate fechaRetiro;

    /** Motivo del catálogo MOTIVO_RETIRO. */
    @Column(name = "motivo_id", nullable = false)
    private Long motivoId;

    /** Copiado del motivo al registrar: la liquidación calcula indemnización. */
    @Column(nullable = false)
    private Boolean indemniza = Boolean.FALSE;

    /** Fecha de fin que tenía la vinculación antes del retiro (null en indefinidos). */
    @Column(name = "fecha_fin_pactada")
    private LocalDate fechaFinPactada;

    /** Cesantías ya consignadas al fondo fuera del sistema hasta esta fecha. */
    @Column(name = "cesantias_corte_hasta")
    private LocalDate cesantiasCorteHasta;

    @Column(length = 500)
    private String observacion;

    /** VIGENTE | ANULADO. */
    @Column(nullable = false, length = 12)
    private String estado = "VIGENTE";

    /** Prenómina de liquidación definitiva generada desde el retiro. */
    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "anulado_at")
    private LocalDateTime anuladoAt;
}
