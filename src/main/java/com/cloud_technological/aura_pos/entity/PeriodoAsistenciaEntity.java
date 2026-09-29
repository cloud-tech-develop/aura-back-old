package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "periodo_asistencia")
public class PeriodoAsistenciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_nomina_id")
    private PeriodoNominaEntity periodoNomina;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "estado", length = 30, nullable = false)
    private String estado = "ABIERTO";
    // ABIERTO | EN_REVISION | APROBADO | BLOQUEADO | ENVIADO_A_NOMINA | ANULADO

    @Column(name = "creado_por")
    private Integer creadoPor;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "cerrado_por")
    private Integer cerradoPor;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "aprobado_por")
    private Integer aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;
}
