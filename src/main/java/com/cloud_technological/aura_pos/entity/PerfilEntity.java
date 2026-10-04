package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Perfil de permisos de una empresa (V190): qué submódulos ve y qué puede hacer
 * en cada uno el usuario que lo tenga. Ver docs/PLAN_PERMISOS.md.
 */
@Entity
@Table(name = "perfil")
@Getter
@Setter
@NoArgsConstructor
public class PerfilEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** Solo en perfiles de sistema (ver {@code PerfilesSistema}). */
    @Column(length = 30)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    /** Todo lo que la empresa tenga activo, incluidos submódulos futuros. */
    @Column(name = "acceso_total", nullable = false)
    private Boolean accesoTotal = false;

    @Column(name = "es_sistema", nullable = false)
    private Boolean esSistema = false;

    @Column(nullable = false)
    private Boolean activo = true;

    /** Descuento máximo por línea o general (V192); null = sin límite. */
    @Column(name = "descuento_max_pct", precision = 5, scale = 2)
    private java.math.BigDecimal descuentoMaxPct;

    /** Rebaja máxima del precio contra el menor precio del producto (V192); null = sin límite. */
    @Column(name = "rebaja_precio_max_pct", precision = 5, scale = 2)
    private java.math.BigDecimal rebajaPrecioMaxPct;

    /** Ve y opera en todas las sedes; si no, solo en las asignadas al usuario (V192). */
    @Column(name = "todas_sedes", nullable = false)
    private Boolean todasSedes = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (createdAt == null) createdAt = ahora;
        updatedAt = ahora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
