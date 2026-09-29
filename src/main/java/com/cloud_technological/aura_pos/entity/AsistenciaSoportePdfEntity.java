package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asistencia_soporte_pdf")
@Getter
@Setter
public class AsistenciaSoportePdfEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "asistencia_frente_id")
    private Long asistenciaFrenteId;

    @Column(name = "plantilla_id")
    private Long plantillaId;

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "frente_id")
    private Long frenteId;

    @Column(name = "lider_id")
    private Long liderId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "archivo_url", length = 500, nullable = false)
    private String archivoUrl;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "peso_archivo")
    private Long pesoArchivo;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "hash_archivo", length = 80)
    private String hashArchivo;

    /** CARGADO | EN_REVISION | APROBADO | RECHAZADO | ANULADO */
    @Column(length = 20)
    private String estado;

    @Column(length = 255)
    private String observacion;

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
        if (estado == null) estado = "CARGADO";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
