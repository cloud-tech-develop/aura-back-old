package com.cloud_technological.aura_pos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
@Table(name = "asistencia_dia")
public class AsistenciaDiaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private EmpleadoEntity empleado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_id")
    private TurnoTrabajoEntity turno;

    @Column(name = "hora_entrada_programada")
    private LocalTime horaEntradaProgramada;

    @Column(name = "hora_salida_programada")
    private LocalTime horaSalidaProgramada;

    @Column(name = "hora_entrada_real")
    private LocalTime horaEntradaReal;

    @Column(name = "hora_salida_real")
    private LocalTime horaSalidaReal;

    @Column(name = "minutos_programados", nullable = false)
    private Integer minutosProgramados = 0;

    @Column(name = "minutos_trabajados", nullable = false)
    private Integer minutosTrabajados = 0;

    @Column(name = "minutos_tarde", nullable = false)
    private Integer minutosTarde = 0;

    @Column(name = "minutos_salida_temprana", nullable = false)
    private Integer minutosSalidaTemprana = 0;

    @Column(name = "minutos_extra_diurna", nullable = false)
    private Integer minutosExtraDiurna = 0;

    @Column(name = "minutos_extra_nocturna", nullable = false)
    private Integer minutosExtraNocturna = 0;

    @Column(name = "minutos_dominical_festiva", nullable = false)
    private Integer minutosDominicalFestiva = 0;

    @Column(name = "minutos_nocturnos", nullable = false)
    private Integer minutosNocturnos = 0;

    @Column(name = "estado_asistencia", length = 30, nullable = false)
    private String estadoAsistencia = "SIN_MARCAJE_COMPLETO";

    @Column(name = "estado_aprobacion", length = 30, nullable = false)
    private String estadoAprobacion = "PENDIENTE";

    @Column(name = "aprobado_por")
    private Integer aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "observacion", length = 255)
    private String observacion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
