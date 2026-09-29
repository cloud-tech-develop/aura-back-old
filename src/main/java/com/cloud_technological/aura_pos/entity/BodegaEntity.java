package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Dónde vive el stock (V172). La sucursal factura y tiene caja; la bodega
 * guarda. Una sucursal tiene al menos una bodega y el saldo de la sucursal es
 * la suma de las suyas.
 */
@Entity
@Table(name = "bodega")
@Getter
@Setter
public class BodegaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private SucursalEntity sucursal;

    @Column(length = 20)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    /** Quién responde por el faltante. Opcional. */
    @Column(name = "responsable_usuario_id")
    private Integer responsableUsuarioId;

    /** La que resuelve el backend cuando el documento no dice bodega. */
    @Column(name = "es_principal", nullable = false)
    private Boolean esPrincipal = Boolean.FALSE;

    /** Averías, cuarentena o tránsito: guardan stock pero el POS no las ofrece. */
    @Column(name = "permite_venta", nullable = false)
    private Boolean permiteVenta = Boolean.TRUE;

    @Column(length = 120)
    private String ubicacion;

    @Column(length = 300)
    private String observacion;

    @Column(nullable = false)
    private Boolean activa = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (esPrincipal == null) esPrincipal = Boolean.FALSE;
        if (permiteVenta == null) permiteVenta = Boolean.TRUE;
        if (activa == null) activa = Boolean.TRUE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
