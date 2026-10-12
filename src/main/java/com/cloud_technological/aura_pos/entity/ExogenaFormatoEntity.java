package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

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

    /** AMBAS | JURIDICA (el 1010 de socios solo lo reportan las sociedades). */
    @Column(name = "aplica_a", nullable = false, length = 10)
    @Builder.Default
    private String aplicaA = "AMBAS";

    /** CONTABILIDAD (mapeos de cuentas) | NOMINA (2276, desde la nómina). */
    @Column(nullable = false, length = 15)
    @Builder.Default
    private String fuente = "CONTABILIDAD";

    /** Columna que debe tener valor para reportar la línea (1003: RETENCION). */
    @Column(name = "columna_requerida", length = 30)
    private String columnaRequerida;

    /** Umbral por defecto de cuantías menores (0 = el formato no las agrupa). */
    @Column(name = "cuantia_menor", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal cuantiaMenor = BigDecimal.ZERO;

    /**
     * Columnas de identificación del formato, en el orden del prevalidador, como
     * códigos separados por coma: CPT, TDOC, NID, DV, APL1, APL2, NOM1, NOM2, RAZ,
     * DIR, DPTO, MUN, PAIS.
     */
    @Column(nullable = false, length = 200)
    private String encabezado;

    public static final String FUENTE_NOMINA = "NOMINA";
    public static final String APLICA_JURIDICA = "JURIDICA";
}
