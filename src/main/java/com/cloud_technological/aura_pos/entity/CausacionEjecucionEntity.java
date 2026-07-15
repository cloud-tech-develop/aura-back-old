package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/** Ejecución mensual de una causación (E6): única por (causación, período). */
@Entity
@Table(name = "causacion_ejecucion",
        uniqueConstraints = @UniqueConstraint(columnNames = { "causacion_id", "periodo" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CausacionEjecucionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "causacion_id", nullable = false)
    private Long causacionId;

    @Column(nullable = false, length = 7)
    private String periodo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
