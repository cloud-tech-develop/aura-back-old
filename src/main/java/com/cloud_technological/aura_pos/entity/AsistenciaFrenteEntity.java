package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asistencia_frente")
@Getter
@Setter
public class AsistenciaFrenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "frente_id", nullable = false)
    private Long frenteId;

    @Column(name = "plantilla_id")
    private Long plantillaId;

    @Column(name = "soporte_pdf_id")
    private Long soportePdfId;

    @Column(name = "lider_id")
    private Long liderId;

    @Column(nullable = false)
    private LocalDate fecha;

    /** BORRADOR | ENVIADO_REVISION | EN_CORRECCION | APROBADO | RECHAZADO | ENVIADO_NOMINA | ANULADO */
    @Column(length = 30)
    private String estado;

    @Column(name = "observacion_lider", length = 500)
    private String observacionLider;

    @Column(name = "observacion_admin", length = 500)
    private String observacionAdmin;

    @Column(name = "enviado_revision_at")
    private LocalDateTime enviadoRevisionAt;

    @Column(name = "aprobado_por")
    private Long aprobadoPor;

    @Column(name = "aprobado_at")
    private LocalDateTime aprobadoAt;

    @Column(name = "rechazado_por")
    private Long rechazadoPor;

    @Column(name = "rechazado_at")
    private LocalDateTime rechazadoAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (estado == null) estado = "BORRADOR";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
