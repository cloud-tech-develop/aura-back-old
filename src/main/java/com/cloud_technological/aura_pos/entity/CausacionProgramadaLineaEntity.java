package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

/** Línea de la plantilla de causación: cuenta + débito o crédito fijo. */
@Entity
@Table(name = "causacion_programada_linea")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CausacionProgramadaLineaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "causacion_id", nullable = false)
    private CausacionProgramadaEntity causacion;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal debito = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal credito = BigDecimal.ZERO;

    @Column(name = "tercero_id")
    private Long terceroId;
}
