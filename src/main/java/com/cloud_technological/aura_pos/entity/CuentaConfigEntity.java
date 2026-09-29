package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Mapea, por empresa, un {@link ConceptoContable} a una cuenta del PUC.
 * Permite que cada empresa personalice qué cuenta usa el motor de asientos
 * para cada concepto sin recompilar. Si no existe fila, el motor cae al código
 * por defecto del concepto.
 */
@Entity
@Table(name = "cuenta_config",
        uniqueConstraints = @UniqueConstraint(columnNames = { "empresa_id", "concepto" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CuentaConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** Nombre del enum {@link ConceptoContable}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConceptoContable concepto;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
