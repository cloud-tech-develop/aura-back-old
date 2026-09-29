package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asistencia_alerta")
@Getter
@Setter
public class AsistenciaAlertaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "asistencia_frente_id")
    private Long asistenciaFrenteId;

    @Column(name = "asistencia_frente_detalle_id")
    private Long asistenciaFrenteDetalleId;

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "frente_id")
    private Long frenteId;

    @Column(name = "empleado_id")
    private Long empleadoId;

    /** TRABAJADOR_NO_ASIGNADO | TRABAJADOR_DUPLICADO_MISMO_DIA | HORAS_EXCESIVAS | SALIDA_MENOR_ENTRADA | ... */
    @Column(name = "tipo_alerta", length = 40)
    private String tipoAlerta;

    /** INFO | ADVERTENCIA | CRITICA */
    @Column(length = 15)
    private String nivel;

    @Column(length = 255)
    private String descripcion;

    /** ABIERTA | REVISADA | RESUELTA | IGNORADA */
    @Column(length = 15)
    private String estado;

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
        if (nivel == null) nivel = "ADVERTENCIA";
        if (estado == null) estado = "ABIERTA";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
