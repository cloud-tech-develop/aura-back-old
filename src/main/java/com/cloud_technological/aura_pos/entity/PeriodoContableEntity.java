package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "periodo_contable")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PeriodoContableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private Short anio;

    @Column(nullable = false)
    private Short mes;

    /** ABIERTO | CERRADO */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String estado = "ABIERTO";

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDate fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDate fechaCierre;

    @Column(name = "usuario_apertura_id")
    private Long usuarioAperturaId;

    @Column(name = "usuario_cierre_id")
    private Long usuarioCierreId;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fechaApertura == null) fechaApertura = LocalDate.now();
    }
}
