package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/** Ajuste fiscal de un renglón: menor o mayor valor fiscal, permanente o temporario (V202). */
@Entity
@Table(name = "conciliacion_fiscal_ajuste")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConciliacionFiscalAjusteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conciliacion_id", nullable = false)
    private Long conciliacionId;

    @Column(nullable = false, length = 10)
    private String renglon;

    /** MENOR | MAYOR */
    @Column(nullable = false, length = 6)
    private String tipo;

    /** PERMANENTE | TEMPORARIA */
    @Column(nullable = false, length = 12)
    private String diferencia;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 300)
    private String concepto;

    @Column(name = "cuenta_codigo", length = 20)
    private String cuentaCodigo;

    @Column(length = 60)
    private String sugerencia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
