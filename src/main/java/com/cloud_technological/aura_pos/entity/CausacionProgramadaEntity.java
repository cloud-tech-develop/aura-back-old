package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

/**
 * Plantilla de asiento recurrente (E6): arriendos, servicios recibidos sin
 * factura, etc. El job mensual genera el asiento en BORRADOR para que el
 * contador lo apruebe.
 */
@Entity
@Table(name = "causacion_programada")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CausacionProgramadaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** Día del mes en que se genera el asiento. */
    @Column(nullable = false)
    @Builder.Default
    private Integer dia = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "causacion", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<CausacionProgramadaLineaEntity> lineas = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
