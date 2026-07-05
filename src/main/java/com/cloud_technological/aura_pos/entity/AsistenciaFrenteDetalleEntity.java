package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asistencia_frente_detalle")
@Getter
@Setter
public class AsistenciaFrenteDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "asistencia_frente_id", nullable = false)
    private Long asistenciaFrenteId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "frente_id", nullable = false)
    private Long frenteId;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;

    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    @Column(name = "horas_trabajadas", precision = 6, scale = 2)
    private BigDecimal horasTrabajadas = BigDecimal.ZERO;

    @Column(name = "horas_ordinarias", precision = 6, scale = 2)
    private BigDecimal horasOrdinarias = BigDecimal.ZERO;

    @Column(name = "horas_extra_diurnas", precision = 6, scale = 2)
    private BigDecimal horasExtraDiurnas = BigDecimal.ZERO;

    @Column(name = "horas_extra_nocturnas", precision = 6, scale = 2)
    private BigDecimal horasExtraNocturnas = BigDecimal.ZERO;

    @Column(name = "horas_dominicales", precision = 6, scale = 2)
    private BigDecimal horasDominicales = BigDecimal.ZERO;

    @Column(name = "horas_festivas", precision = 6, scale = 2)
    private BigDecimal horasFestivas = BigDecimal.ZERO;

    @Column(name = "horas_ordinarias_diurnas", precision = 6, scale = 2)
    private BigDecimal horasOrdinariasDiurnas = BigDecimal.ZERO;

    @Column(name = "horas_ordinarias_nocturnas", precision = 6, scale = 2)
    private BigDecimal horasOrdinariasNocturnas = BigDecimal.ZERO;

    /** Horas ordinarias trabajadas en día dominical/festivo (recargo dominical). */
    @Column(name = "horas_dominicales_festivas", precision = 6, scale = 2)
    private BigDecimal horasDominicalesFestivas = BigDecimal.ZERO;

    @Column(name = "horas_extra_diurnas_dom_fest", precision = 6, scale = 2)
    private BigDecimal horasExtraDiurnasDomFest = BigDecimal.ZERO;

    @Column(name = "horas_extra_nocturnas_dom_fest", precision = 6, scale = 2)
    private BigDecimal horasExtraNocturnasDomFest = BigDecimal.ZERO;

    @Column(name = "valor_hora_base", precision = 15, scale = 2)
    private BigDecimal valorHoraBase;

    @Column(name = "valor_calculado_estimado", precision = 15, scale = 2)
    private BigDecimal valorCalculadoEstimado;

    @Column(name = "requiere_revision")
    private Boolean requiereRevision = Boolean.FALSE;

    /** ASISTIO | NO_ASISTIO | LLEGO_TARDE | SALIO_TEMPRANO | PERMISO | INCAPACIDAD | VACACIONES | SUSPENDIDO | SIN_REGISTRO */
    @Column(name = "estado_asistencia", length = 20)
    private String estadoAsistencia;

    /** PENDIENTE | APROBADO | RECHAZADO | AJUSTADO | ENVIADO_NOMINA */
    @Column(name = "estado_revision", length = 20)
    private String estadoRevision;

    @Column(name = "observacion_lider", length = 255)
    private String observacionLider;

    @Column(name = "observacion_admin", length = 255)
    private String observacionAdmin;

    @Column(name = "aprobado_por")
    private Long aprobadoPor;

    @Column(name = "aprobado_at")
    private LocalDateTime aprobadoAt;

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
        if (estadoAsistencia == null) estadoAsistencia = "SIN_REGISTRO";
        if (estadoRevision == null) estadoRevision = "PENDIENTE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
