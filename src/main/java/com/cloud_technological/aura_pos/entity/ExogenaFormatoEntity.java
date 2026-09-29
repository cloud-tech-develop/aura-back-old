package com.cloud_technological.aura_pos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Formato de información exógena DIAN (E11): catálogo global (1001, 1005,
 * 1006, 1007, 1008, 1009, 2276) sembrado por migración; no depende de la
 * empresa.
 */
@Entity
@Table(name = "exogena_formato")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExogenaFormatoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(name = "version_dian", nullable = false)
    private Integer versionDian;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
