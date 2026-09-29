package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "nomina_config")
public class NominaConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @Column(name = "modo_nomina", length = 20, nullable = false)
    private String modoNomina = "SIMPLIFICADO"; // COMPLETO | SIMPLIFICADO

    @Column(name = "periodicidad", length = 20, nullable = false)
    private String periodicidad = "MENSUAL"; // MENSUAL | QUINCENAL | SEMANAL

    @Column(name = "modo_liquidacion", length = 30, nullable = false)
    private String modoLiquidacion = "SIN_ASISTENCIA";
    // SIN_ASISTENCIA | CON_ASISTENCIA_OBLIGATORIA | MIXTA

    @Column(name = "smmlv", nullable = false, precision = 15, scale = 2)
    private BigDecimal smmlv = new BigDecimal("1423500");

    @Column(name = "auxilio_transporte", nullable = false, precision = 15, scale = 2)
    private BigDecimal auxilioTransporte = new BigDecimal("200000");

    @Column(name = "pct_salud_empleado", nullable = false, precision = 5, scale = 2)
    private BigDecimal pctSaludEmpleado = new BigDecimal("4.00");

    @Column(name = "pct_pension_empleado", nullable = false, precision = 5, scale = 2)
    private BigDecimal pctPensionEmpleado = new BigDecimal("4.00");

    @Column(name = "pct_salud_empleador", nullable = false, precision = 5, scale = 2)
    private BigDecimal pctSaludEmpleador = new BigDecimal("8.50");

    @Column(name = "pct_pension_empleador", nullable = false, precision = 5, scale = 2)
    private BigDecimal pctPensionEmpleador = new BigDecimal("12.00");

    @Column(name = "pct_caja_compensacion", nullable = false, precision = 5, scale = 2)
    private BigDecimal pctCajaCompensacion = new BigDecimal("4.00");

    @Column(name = "pct_icbf", nullable = false, precision = 5, scale = 2)
    private BigDecimal pctIcbf = new BigDecimal("3.00");

    @Column(name = "pct_sena", nullable = false, precision = 5, scale = 2)
    private BigDecimal pctSena = new BigDecimal("2.00");

    /** B-10 — apoyo de sostenimiento del aprendiz como % del SMMLV, por fase. */
    @Column(name = "aprendiz_pct_lectiva", nullable = false, precision = 5, scale = 2)
    private BigDecimal aprendizPctLectiva = new BigDecimal("50.00");

    @Column(name = "aprendiz_pct_practica", nullable = false, precision = 5, scale = 2)
    private BigDecimal aprendizPctPractica = new BigDecimal("75.00");

    // ── Topes y exoneraciones (V107) ────────────────────────────────────────
    // Parametrizado por empresa, no hardcodeado: la exoneración depende del
    // tipo de sociedad y los topes cambian por ley.

    /**
     * Ley 1607 art. 114-1: sociedades declarantes de renta no pagan salud
     * empleador, SENA ni ICBF por empleados que devenguen menos del umbral.
     *
     * <p>Default FALSE por prudencia: activarlo es decisión del cliente con su
     * contador. Pero ojo — a quien le aplica y no lo activa, se le está
     * cobrando de más.
     */
    @Column(name = "aplica_exoneracion_1607", nullable = false)
    private Boolean aplicaExoneracion1607 = Boolean.FALSE;

    /** Umbral de la exoneración, en SMMLV. Se evalúa POR EMPLEADO. */
    @Column(name = "umbral_exoneracion_smmlv", nullable = false, precision = 5, scale = 2)
    private BigDecimal umbralExoneracionSmmlv = new BigDecimal("10");

    /** Tope del IBC en SMMLV. El aporte va sobre MIN(baseIbc, tope * smmlv). */
    @Column(name = "tope_ibc_smmlv", nullable = false, precision = 5, scale = 2)
    private BigDecimal topeIbcSmmlv = new BigDecimal("25");

    /** Fondo de solidaridad pensional: aporte adicional sobre 4 SMMLV. */
    @Column(name = "aplica_fondo_solidaridad", nullable = false)
    private Boolean aplicaFondoSolidaridad = Boolean.TRUE;

    /** Salario integral: el IBC es este % (factor prestacional = 100 − esto). */
    @Column(name = "factor_salario_integral", nullable = false, precision = 5, scale = 2)
    private BigDecimal factorSalarioIntegral = new BigDecimal("70");

    /** F3 — ¿se pueden tomar vacaciones sin días causados suficientes? */
    @Column(name = "permite_vacaciones_anticipadas", nullable = false)
    private Boolean permiteVacacionesAnticipadas = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
