package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Mapea, por empresa, un método de pago (EFECTIVO, TRANSFERENCIA, NEQUI…) a
 * una cuenta contable del disponible (11xx). El motor de asientos la usa como
 * segunda prioridad: cuenta bancaria → forma de pago → fallback CAJA/BANCOS.
 */
@Entity
@Table(name = "forma_pago_contable",
        uniqueConstraints = @UniqueConstraint(columnNames = { "empresa_id", "codigo" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FormaPagoContableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** Código del método de pago tal como viaja en los documentos (upper-case). */
    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "cuenta_contable_id")
    private Long cuentaContableId;

    /** Si es true, el pago debe traer cuenta bancaria (transferencias). */
    @Column(name = "requiere_cuenta_bancaria", nullable = false)
    @Builder.Default
    private Boolean requiereCuentaBancaria = Boolean.FALSE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
