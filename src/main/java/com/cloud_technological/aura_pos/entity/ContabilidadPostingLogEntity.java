package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Vista positiva de auditoría del posting automático (E3): qué se
 * contabilizó, desde dónde y qué falló. Solo se inserta, nunca se edita.
 */
@Entity
@Table(name = "contabilidad_posting_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContabilidadPostingLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "tipo_origen", nullable = false, length = 30)
    private String tipoOrigen;

    @Column(name = "origen_id", nullable = false)
    private Long origenId;

    @Column(name = "asiento_id")
    private Long asientoId;

    /** EXITO | ERROR */
    @Column(nullable = false, length = 10)
    private String estado;

    @Column(length = 500)
    private String error;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
