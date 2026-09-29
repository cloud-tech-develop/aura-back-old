package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "calendario_laboral")
@Getter
@Setter
public class CalendarioLaboralEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private LocalDate fecha;

    /** LABORAL | DOMINGO | FESTIVO_NACIONAL | FESTIVO_REGIONAL | DESCANSO_EMPRESA | CIERRE_OPERATIVO | COMPENSATORIO */
    @Column(name = "tipo_dia", length = 30)
    private String tipoDia;

    @Column(length = 150)
    private String nombre;

    @Column(name = "aplica_recargo")
    private Boolean aplicaRecargo = Boolean.TRUE;

    @Column(name = "es_festivo_nacional")
    private Boolean esFestivoNacional = Boolean.FALSE;

    @Column(name = "es_festivo_regional")
    private Boolean esFestivoRegional = Boolean.FALSE;

    @Column(name = "es_descanso_empresa")
    private Boolean esDescansoEmpresa = Boolean.FALSE;

    /** SISTEMA | MANUAL | IMPORTADO */
    @Column(length = 20)
    private String origen = "MANUAL";

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
        if (origen == null) origen = "MANUAL";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
