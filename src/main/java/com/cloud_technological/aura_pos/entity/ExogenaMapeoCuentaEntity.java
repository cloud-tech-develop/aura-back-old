package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Mapeo cuenta/rango PUC → concepto de exógena por empresa (E11).
 * cuentaHasta null = match por prefijo de cuentaDesde; con valor = rango
 * lexicográfico. Al generar gana el mapeo con el prefijo más específico
 * (5105 le gana a 51).
 */
@Entity
@Table(name = "exogena_mapeo_cuenta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExogenaMapeoCuentaEntity {

    public static final String MOVIMIENTO_DB = "MOVIMIENTO_DB";
    public static final String MOVIMIENTO_CR = "MOVIMIENTO_CR";
    public static final String SALDO_DB = "SALDO_DB";
    public static final String SALDO_CR = "SALDO_CR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "concepto_id", nullable = false)
    private Long conceptoId;

    @Column(name = "cuenta_desde", nullable = false, length = 10)
    private String cuentaDesde;

    @Column(name = "cuenta_hasta", length = 10)
    private String cuentaHasta;

    /** MOVIMIENTO_DB | MOVIMIENTO_CR (del año) · SALDO_DB | SALDO_CR (a dic 31). */
    @Column(name = "tipo_valor", nullable = false, length = 20)
    private String tipoValor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
