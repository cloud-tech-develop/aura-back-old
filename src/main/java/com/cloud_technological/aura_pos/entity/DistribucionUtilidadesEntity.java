package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Distribución de utilidades post-asamblea (E8): reserva legal (330505) y
 * dividendos decretados (2360) contra resultados acumulados (3705).
 */
@Entity
@Table(name = "distribucion_utilidades")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DistribucionUtilidadesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private Integer anio;

    /** Saldo de 3705 al momento de distribuir (auditoría de la decisión). */
    @Column(name = "utilidad_base", nullable = false, precision = 18, scale = 2)
    private BigDecimal utilidadBase;

    @Column(name = "reserva_legal", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservaLegal;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal dividendos;

    @Column(length = 300)
    private String observaciones;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
