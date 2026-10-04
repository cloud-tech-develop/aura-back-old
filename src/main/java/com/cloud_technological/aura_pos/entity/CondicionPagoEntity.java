package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/** Condición de pago de la factura (Contado, 30, 60, 90 días): define el vencimiento. */
@Entity
@Table(name = "condicion_pago")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CondicionPagoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 80)
    private String nombre;

    /** Días de plazo; 0 = contado. */
    @Column(nullable = false)
    @Builder.Default
    private Integer dias = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;

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
