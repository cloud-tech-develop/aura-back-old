package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Hallazgo del validador previo de exógena (E11): tercero incompleto, cuenta
 * sin mapeo, comprobantes en borrador, período abierto o movimiento sin
 * tercero. Se persiste junto con el lote generado.
 */
@Entity
@Table(name = "exogena_error")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExogenaErrorEntity {

    public static final String TERCERO_INCOMPLETO = "TERCERO_INCOMPLETO";
    public static final String SIN_MAPEO = "SIN_MAPEO";
    public static final String COMPROBANTE_BORRADOR = "COMPROBANTE_BORRADOR";
    public static final String PERIODO_ABIERTO = "PERIODO_ABIERTO";
    public static final String SIN_TERCERO = "SIN_TERCERO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lote_id")
    private Long loteId;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(length = 300)
    private String detalle;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
