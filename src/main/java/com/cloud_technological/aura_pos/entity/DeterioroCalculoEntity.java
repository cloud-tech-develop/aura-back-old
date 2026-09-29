package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Propuesta de deterioro de cartera por edades (E6). Su asiento
 * (DB 5199 · CR 1399) nace SIEMPRE en borrador: el contador aprueba.
 */
@Entity
@Table(name = "deterioro_calculo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeterioroCalculoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    /** Resumen de tramos aplicados (auditoría de la propuesta). */
    @Column(length = 500)
    private String detalle;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
