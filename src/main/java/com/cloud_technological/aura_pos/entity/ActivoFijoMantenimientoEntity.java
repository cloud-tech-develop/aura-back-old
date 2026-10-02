package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Mantenimiento de un activo fijo (V186): historial, costo y próximo. No contabiliza: el pago va por gastos. */
@Entity
@Table(name = "activo_fijo_mantenimiento")
@Getter
@Setter
public class ActivoFijoMantenimientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "activo_id", nullable = false)
    private Long activoId;

    @Column(nullable = false)
    private LocalDate fecha;

    /** PREVENTIVO | CORRECTIVO */
    @Column(nullable = false, length = 20)
    private String tipo = "PREVENTIVO";

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal costo = BigDecimal.ZERO;

    @Column(name = "tercero_id")
    private Long terceroId;

    private LocalDate proximo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
