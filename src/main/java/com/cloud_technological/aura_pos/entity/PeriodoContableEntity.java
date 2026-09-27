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

    /** Cuántas veces se ha reabierto este mes (V171). */
    @Column(nullable = false)
    @Builder.Default
    private Integer reaperturas = 0;

    @Column(name = "fecha_reapertura")
    private LocalDateTime fechaReapertura;

    @Column(name = "usuario_reapertura_id")
    private Long usuarioReaperturaId;

    @Column(name = "motivo_reapertura", length = 300)
    private String motivoReapertura;

    /** True si lo abrió el sistema al llegar el primer documento del mes. */
    @Column(name = "creado_automatico", nullable = false)
    @Builder.Default
    private Boolean creadoAutomatico = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fechaApertura == null) fechaApertura = LocalDate.now();
    }
}
