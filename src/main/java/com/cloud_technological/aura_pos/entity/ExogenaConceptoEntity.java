package com.cloud_technological.aura_pos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Concepto DIAN dentro de un formato de exógena (E11): 5001, 5002… del 1001;
 * los formatos sin conceptos propios usan uno único con el código del formato.
 */
@Entity
@Table(name = "exogena_concepto")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExogenaConceptoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "formato_id", nullable = false)
    private Long formatoId;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false, length = 255)
    private String nombre;
}
