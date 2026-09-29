package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asistencia_frente_aprobacion")
@Getter
@Setter
public class AsistenciaFrenteAprobacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "asistencia_frente_id", nullable = false)
    private Long asistenciaFrenteId;

    @Column(name = "asistencia_frente_detalle_id")
    private Long asistenciaFrenteDetalleId;

    @Column(name = "administrador_id")
    private Long administradorId;

    /** APROBAR | RECHAZAR | SOLICITAR_CORRECCION | AJUSTAR | ANULAR */
    @Column(length = 30)
    private String accion;

    @Column(name = "valor_anterior", length = 255)
    private String valorAnterior;

    @Column(name = "valor_aprobado", length = 255)
    private String valorAprobado;

    @Column(length = 500)
    private String observacion;

    @Column(name = "created_by")
    private Long createdBy;

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
    }
}
