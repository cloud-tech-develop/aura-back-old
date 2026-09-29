package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

/**
 * Línea de un lote de exógena (E11): valor por tercero × concepto.
 * terceroId null + cuantiaMenor = agrupado bajo el NIT 222222222.
 */
@Entity
@Table(name = "exogena_linea")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExogenaLineaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(name = "concepto_id", nullable = false)
    private Long conceptoId;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(name = "cuantia_menor", nullable = false)
    @Builder.Default
    private Boolean cuantiaMenor = false;
}
