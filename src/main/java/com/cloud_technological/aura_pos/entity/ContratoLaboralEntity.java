package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Vínculo laboral (V102).
 *
 * <p>Antes el contrato estaba embebido en {@link EmpleadoEntity}
 * (fecha_ingreso, salario_base, tipo_contrato). Eso impedía tres cosas:
 * multi-vínculo simultáneo, historial de contratos, e historial salarial
 * —un aumento sobrescribía {@code salario_base} y el dato anterior se perdía,
 * dejando sin base los retroactivos, la auditoría y la bandera {@code vsp}
 * de PILA.
 *
 * <p>Reparto de responsabilidades:
 * <ul>
 *   <li>{@link TerceroEntity}  → quién es la persona</li>
 *   <li>{@link EmpleadoEntity} → vínculo persona ↔ empresa</li>
 *   <li>{@code ContratoLaboralEntity} → las condiciones laborales</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "contrato_laboral")
public class ContratoLaboralEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private EmpleadoEntity empleado;

    @Column(name = "tipo_contrato", length = 30, nullable = false)
    private String tipoContrato;   // INDEFINIDO | FIJO | OBRA_LABOR | PRESTACION_SERVICIOS | APRENDIZAJE

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** NULL = indefinido. */
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /**
     * Salario vigente. Denormalización de lectura: la fuente de verdad del
     * histórico es {@link ContratoSalarioHistorialEntity}. Al cambiarlo hay que
     * cerrar la fila vigente y abrir una nueva — ver ContratoLaboralService.
     */
    @Column(name = "salario_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal salarioBase;

    /** El IBC es el 70% (factor prestacional 30%) y NO se provisionan prestaciones. */
    @Column(name = "es_salario_integral", nullable = false)
    private Boolean esSalarioIntegral = Boolean.FALSE;

    /**
     * B-10 — fase del aprendiz SENA: {@code LECTIVA} o {@code PRACTICA}. Solo aplica
     * cuando {@code tipoContrato = APRENDIZAJE}; para el resto queda {@code null}.
     * Define el apoyo de sostenimiento (50% / 75% SMMLV) y si cotiza ARL (solo en
     * práctica).
     */
    @Column(name = "fase", length = 20)
    private String fase;

    /** NULL = hereda de nomina_config. */
    @Column(name = "periodicidad", length = 20)
    private String periodicidad;

    /** Con multi-vínculo, cuál manda para reportes de un solo contrato. */
    @Column(name = "es_principal", nullable = false)
    private Boolean esPrincipal = Boolean.TRUE;

    // ── Retefuente (se usa en Fase 4.5; la columna la crea V102) ────────────

    @Column(name = "procedimiento_retefuente", length = 3, nullable = false)
    private String procedimientoRetefuente = "1";

    @Column(name = "porcentaje_fijo_retencion", precision = 5, scale = 2)
    private BigDecimal porcentajeFijoRetencion;

    // ── Estado ──────────────────────────────────────────────────────────────

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "ACTIVO";   // ACTIVO | SUSPENDIDO | TERMINADO

    /** Determina si hay indemnización y cómo se calcula (Fase 8). */
    @Column(name = "causa_retiro", length = 40)
    private String causaRetiro;

    @Column(name = "observacion", length = 500)
    private String observacion;

    // ── Relaciones ──────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ContratoSalarioHistorialEntity> historialSalarios = new ArrayList<>();

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ContratoCentroCostoEntity> centrosCosto = new ArrayList<>();

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ContratoAfiliacionEntity> afiliaciones = new ArrayList<>();

    // ── Seguridad social (V110) ─────────────────────────────────────────────

    /** Código UGPP: 01 = dependiente, 12 = aprendiz lectiva, etc. Lo exige PILA. */
    @Column(name = "tipo_cotizante", length = 5)
    private String tipoCotizante;

    @Column(name = "subtipo_cotizante", length = 5)
    private String subtipoCotizante;

    /** Determina la tarifa ARL cuando se resuelve por sede, no por contrato. */
    @Column(name = "centro_trabajo_id")
    private Long centroTrabajoId;

    /**
     * Nivel de riesgo ARL (1-5). Migrado desde {@code empleado_arl}.
     *
     * <p>⚠️ Pendiente #8: ¿la tarifa se resuelve por contrato o por centro de
     * trabajo? Están ambos caminos; hay que elegir uno y quitar el otro.
     */
    @Column(name = "nivel_riesgo_arl")
    private Integer nivelRiesgoArl;

    @Column(name = "tarifa_arl", precision = 5, scale = 3)
    private BigDecimal tarifaArl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Vigente en una fecha dada. Lo usan la liquidación y PILA. */
    @Transient
    public boolean estaVigenteEn(LocalDate fecha) {
        if (!"ACTIVO".equals(estado) || deletedAt != null) return false;
        if (fecha.isBefore(fechaInicio)) return false;
        return fechaFin == null || !fecha.isAfter(fechaFin);
    }

    /**
     * Salario vigente en una fecha, desde el histórico.
     *
     * <p>Distinto de {@link #getSalarioBase()}, que es el actual. Para liquidar
     * un retroactivo o calcular la bandera {@code vsp} de PILA hay que preguntar
     * por fecha, no por el valor de hoy.
     */
    @Transient
    public BigDecimal salarioEnFecha(LocalDate fecha) {
        return historialSalarios.stream()
                .filter(h -> !fecha.isBefore(h.getFechaDesde()))
                .filter(h -> h.getFechaHasta() == null || !fecha.isAfter(h.getFechaHasta()))
                .map(ContratoSalarioHistorialEntity::getSalario)
                .findFirst()
                .orElse(salarioBase);
    }
}
