package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Concepto amigable de caja (ej. "Matada de cerdo", "Compra de jugos") que el
 * cajero elige al registrar un ingreso/egreso. Cada concepto mapea a una cuenta
 * contable de contrapartida, de modo que el movimiento de caja pueda contabilizarse
 * sin que el cajero conozca el plan de cuentas.
 */
@Entity
@Table(name = "concepto_caja")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConceptoCajaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** INGRESO | EGRESO */
    @Column(nullable = false, length = 10)
    private String tipo;

    /** Cuenta contable de contrapartida del asiento. */
    @Column(name = "cuenta_contable_id", nullable = false)
    private Long cuentaContableId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
