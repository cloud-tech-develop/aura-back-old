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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
