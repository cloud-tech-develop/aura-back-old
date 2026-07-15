package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cuota mensual de amortización de un gasto diferido (E6): cada fila genera
 * un asiento DB gasto · CR 1705. Única por (gasto, período).
 */
@Entity
@Table(name = "diferido_amortizacion",
        uniqueConstraints = @UniqueConstraint(columnNames = { "gasto_id", "periodo" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiferidoAmortizacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "gasto_id", nullable = false)
    private Long gastoId;

    /** 'yyyy-MM' del mes amortizado. */
    @Column(nullable = false, length = 7)
    private String periodo;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
