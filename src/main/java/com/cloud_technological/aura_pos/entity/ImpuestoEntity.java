package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Impuesto parametrizable (E5): define el % y las cuentas del impuesto —
 * generado en ventas (240801) y descontable en compras (240802). El cálculo
 * sigue viviendo en el documento; el asiento agrupa por estas cuentas.
 */
@Entity
@Table(name = "impuesto",
        uniqueConstraints = @UniqueConstraint(columnNames = { "empresa_id", "nombre" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImpuestoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 80)
    private String nombre;

    /** IVA | INC | EXCLUIDO | EXENTO */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, precision = 6, scale = 3)
    @Builder.Default
    private BigDecimal porcentaje = BigDecimal.ZERO;

    /** Cuenta del impuesto en ventas (240801). */
    @Column(name = "cuenta_generado_id")
    private Long cuentaGeneradoId;

    /** Cuenta del impuesto en compras (240802). */
    @Column(name = "cuenta_descontable_id")
    private Long cuentaDescontableId;

    @Column(name = "vigente_desde")
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

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
