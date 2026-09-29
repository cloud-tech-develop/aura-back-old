package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "proyecto")
@Getter
@Setter
public class ProyectoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /** ACTIVO | SUSPENDIDO | FINALIZADO | ANULADO */
    @Column(length = 20)
    private String estado;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    @Column(name = "responsable_administrativo_id")
    private Long responsableAdministrativoId;

    @Column(name = "requiere_control_asistencia")
    private Boolean requiereControlAsistencia = Boolean.TRUE;

    @Column(length = 100)
    private String ciudad;

    @Column(length = 200)
    private String ubicacion;

    @Column(columnDefinition = "TEXT")
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
        if (estado == null) estado = "ACTIVO";
        if (requiereControlAsistencia == null) requiereControlAsistencia = Boolean.TRUE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
