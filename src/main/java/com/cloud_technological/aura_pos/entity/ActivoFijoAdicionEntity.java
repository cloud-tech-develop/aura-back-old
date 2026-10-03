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

/**
 * Adición o mejora que se capitaliza en el activo (V186): sube su costo y,
 * si alarga su vida, los meses. Asiento DB cuenta del activo · CR contrapartida.
 */
@Entity
@Table(name = "activo_fijo_adicion")
@Getter
@Setter
public class ActivoFijoAdicionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "activo_id", nullable = false)
    private Long activoId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(name = "meses_adicionales", nullable = false)
    private Integer mesesAdicionales = 0;

    @Column(name = "cuenta_contrapartida_id", nullable = false)
    private Long cuentaContrapartidaId;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "asiento_id")
    private Long asientoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
