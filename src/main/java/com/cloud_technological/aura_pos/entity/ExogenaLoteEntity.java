package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Lote de exógena (E11): una generación por empresa+formato+año, versionada.
 * BORRADOR se puede regenerar; APROBADO queda bloqueado y una nueva
 * generación crea la versión siguiente.
 */
@Entity
@Table(name = "exogena_lote")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExogenaLoteEntity {

    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_APROBADO = "APROBADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "formato_id", nullable = false)
    private Long formatoId;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(nullable = false, length = 15)
    @Builder.Default
    private String estado = ESTADO_BORRADOR;

    /** Terceros bajo este total van agrupados como cuantías menores (222222222). */
    @Column(name = "cuantia_menor_umbral", nullable = false, precision = 18, scale = 2)
    private BigDecimal cuantiaMenorUmbral;

    @Column(name = "generado_por")
    private Long generadoPor;

    @Column(name = "generado_en", nullable = false)
    private LocalDateTime generadoEn;

    @Column(name = "aprobado_por")
    private Long aprobadoPor;

    @Column(name = "aprobado_en")
    private LocalDateTime aprobadoEn;

    @PrePersist
    void prePersist() {
        if (this.generadoEn == null) this.generadoEn = LocalDateTime.now();
    }
}
