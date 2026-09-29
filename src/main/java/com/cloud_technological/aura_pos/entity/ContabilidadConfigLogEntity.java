package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Auditoría del mapeo concepto→cuenta: una fila por cada cambio de
 * {@link CuentaConfigEntity}. Solo se inserta, nunca se edita.
 */
@Entity
@Table(name = "contabilidad_config_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContabilidadConfigLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConceptoContable concepto;

    @Column(name = "cuenta_anterior_id")
    private Long cuentaAnteriorId;

    @Column(name = "cuenta_nueva_id", nullable = false)
    private Long cuentaNuevaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
