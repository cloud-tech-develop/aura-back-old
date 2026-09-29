package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "centros_costos")
@Getter
@Setter
public class CentroCostoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centro_costo_padre_id")
    private CentroCostoEntity padre;

    /** OPERATIVO | ADMINISTRATIVO | VENTAS | PRODUCCION | FINANCIERO */
    @Column(length = 50)
    private String tipo;

    private Integer nivel;

    @Column(name = "permite_movimientos")
    private Boolean permiteMovimientos = Boolean.TRUE;

    @Column(name = "presupuesto_asignado", precision = 15, scale = 2)
    private BigDecimal presupuestoAsignado;

    @Column(name = "responsable_id")
    private Long responsableId;

    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "usuario_creacion")
    private Long usuarioCreacion;

    @Column(name = "usuario_modificacion")
    private Long usuarioModificacion;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (activo == null) activo = Boolean.TRUE;
        if (permiteMovimientos == null) permiteMovimientos = Boolean.TRUE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
