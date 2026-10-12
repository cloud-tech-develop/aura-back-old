package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cargo de la empresa: nivel, tipo, número de plazas y rango salarial. La
 * vinculación del empleado apunta a un cargo de este catálogo.
 */
@Entity
@Table(name = "nomina_cargo")
@Getter
@Setter
public class NominaCargoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Elemento del catálogo NIVEL_CARGO. */
    @Column(name = "nivel_id")
    private Long nivelId;

    /** Elemento del catálogo TIPO_CARGO. */
    @Column(name = "tipo_cargo_id")
    private Long tipoCargoId;

    /** Número de plazas aprobadas (null = sin límite). */
    private Integer plazas;

    @Column(name = "salario_referencia", precision = 18, scale = 2)
    private BigDecimal salarioReferencia;

    @Column(name = "salario_minimo", precision = 18, scale = 2)
    private BigDecimal salarioMinimo;

    @Column(name = "salario_maximo", precision = 18, scale = 2)
    private BigDecimal salarioMaximo;

    @Column(columnDefinition = "text")
    private String mision;

    @Column(columnDefinition = "text")
    private String funciones;

    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
