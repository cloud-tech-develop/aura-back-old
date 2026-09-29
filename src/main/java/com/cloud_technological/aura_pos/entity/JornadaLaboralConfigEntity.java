package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Config legal de jornada y recargos, con vigencia (Ley 2101/2466). */
@Entity
@Table(name = "jornada_laboral_config")
@Getter
@Setter
public class JornadaLaboralConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @Column(name = "fecha_fin_vigencia")
    private LocalDate fechaFinVigencia;

    @Column(name = "horas_semanales_legales", precision = 5, scale = 2)
    private BigDecimal horasSemanalesLegales = new BigDecimal("42");

    @Column(name = "horas_mensuales_base", precision = 6, scale = 2)
    private BigDecimal horasMensualesBase = new BigDecimal("210");

    @Column(name = "hora_diurna_inicio")
    private LocalTime horaDiurnaInicio = LocalTime.of(6, 0);

    @Column(name = "hora_diurna_fin")
    private LocalTime horaDiurnaFin = LocalTime.of(19, 0);

    @Column(name = "hora_nocturna_inicio")
    private LocalTime horaNocturnaInicio = LocalTime.of(19, 0);

    @Column(name = "hora_nocturna_fin")
    private LocalTime horaNocturnaFin = LocalTime.of(6, 0);

    @Column(name = "recargo_nocturno", precision = 5, scale = 2)
    private BigDecimal recargoNocturno = new BigDecimal("35");

    @Column(name = "recargo_extra_diurna", precision = 5, scale = 2)
    private BigDecimal recargoExtraDiurna = new BigDecimal("25");

    @Column(name = "recargo_extra_nocturna", precision = 5, scale = 2)
    private BigDecimal recargoExtraNocturna = new BigDecimal("75");

    @Column(name = "recargo_dominical_festivo", precision = 5, scale = 2)
    private BigDecimal recargoDominicalFestivo = new BigDecimal("90");

    @Column(name = "max_horas_extra_dia", precision = 5, scale = 2)
    private BigDecimal maxHorasExtraDia = new BigDecimal("2");

    @Column(name = "max_horas_extra_semana", precision = 5, scale = 2)
    private BigDecimal maxHorasExtraSemana = new BigDecimal("12");

    @Column(name = "aplica_excepcion_sectorial")
    private Boolean aplicaExcepcionSectorial = Boolean.FALSE;

    @Column(name = "sector_excepcion", length = 80)
    private String sectorExcepcion;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
