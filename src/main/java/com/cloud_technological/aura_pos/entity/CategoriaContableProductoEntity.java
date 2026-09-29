package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Categoría contable de producto (E4): define a qué cuentas van el ingreso,
 * el inventario, el costo y la devolución de los productos que la usan.
 * Separada de la categoría comercial: taxonomía de venta ≠ parametrización
 * contable.
 */
@Entity
@Table(name = "categoria_contable_producto",
        uniqueConstraints = @UniqueConstraint(columnNames = { "empresa_id", "nombre" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CategoriaContableProductoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 80)
    private String nombre;

    /** BIEN | SERVICIO | INSUMO | ACTIVO_FIJO — SERVICIO no genera par COGS. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String tipo = "BIEN";

    @Column(name = "cuenta_ingreso_id")
    private Long cuentaIngresoId;

    @Column(name = "cuenta_inventario_id")
    private Long cuentaInventarioId;

    @Column(name = "cuenta_costo_id")
    private Long cuentaCostoId;

    /** Cuenta de devolución en ventas (4175); null → misma de ingreso. */
    @Column(name = "cuenta_devolucion_id")
    private Long cuentaDevolucionId;

    /** FK a impuesto (E5); aún sin uso. */
    @Column(name = "impuesto_id")
    private Long impuestoId;

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
