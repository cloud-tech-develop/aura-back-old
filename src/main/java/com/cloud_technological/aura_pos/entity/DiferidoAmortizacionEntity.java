package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cuota mensual de amortización de un diferido: cada fila genera un asiento
 * DB gasto · CR la cuenta del diferido. Única por (gasto, período) o por
 * (diferido, período).
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

    /** Diferido que nació de un gasto (E6). Null si viene de {@link #diferidoId}. */
    @Column(name = "gasto_id")
    private Long gastoId;

    /** Diferido que nació de otro documento, p. ej. una compra (V185). */
    @Column(name = "diferido_id")
    private Long diferidoId;

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
