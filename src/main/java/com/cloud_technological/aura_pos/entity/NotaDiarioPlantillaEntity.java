package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Nota contable que se repite (amortización de un seguro, provisión fija).
 * Si es recurrente, el programador deja un BORRADOR el día {@code diaMes} de
 * cada mes; el contador lo revisa y lo contabiliza.
 */
@Entity
@Table(name = "nota_diario_plantilla")
@Getter
@Setter
public class NotaDiarioPlantillaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Concepto con el que nacen las notas generadas. */
    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(length = 30)
    private String clasificacion;

    @Column(nullable = false)
    private Boolean recurrente = Boolean.FALSE;

    @Column(name = "dia_mes")
    private Short diaMes;

    /** 'YYYY-MM' del último mes generado. */
    @Column(name = "ultimo_periodo", length = 7)
    private String ultimoPeriodo;

    @Column(nullable = false)
    private Boolean activa = Boolean.TRUE;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    private List<NotaDiarioPlantillaLineaEntity> lineas = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
