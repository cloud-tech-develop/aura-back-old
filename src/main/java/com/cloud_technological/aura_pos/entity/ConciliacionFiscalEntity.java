package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Conciliación fiscal de un año, formato 2516 o 2517 (V202,
 * docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §5).
 */
@Entity
@Table(name = "conciliacion_fiscal")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConciliacionFiscalEntity {

    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private Integer anio;

    /** 2516 | 2517 */
    @Column(nullable = false, length = 4)
    private String formato;

    @Column(name = "tarifa_renta", nullable = false, precision = 5, scale = 2)
    private BigDecimal tarifaRenta;

    /** EXCEL | SISTEMA */
    @Column(length = 10)
    private String origen;

    @Column(name = "cargado_at")
    private LocalDateTime cargadoAt;

    @Column(nullable = false, length = 10)
    private String estado;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
