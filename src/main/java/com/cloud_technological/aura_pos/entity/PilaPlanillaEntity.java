package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro tipo 01 de PILA: la planilla (V111).
 */
@Getter
@Setter
@Entity
@Table(name = "pila_planilla")
public class PilaPlanillaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pila_encabezado_id", nullable = false)
    private PilaEncabezadoEntity encabezado;

    @Column(name = "tipo_registro", length = 2, nullable = false)
    private String tipoRegistro = "01";

    @Column(name = "modalidad", length = 2)
    private String modalidad;

    @Column(name = "secuencia", nullable = false)
    private Integer secuencia = 1;

    /** E = empleados, etc. Códigos del operador. */
    @Column(name = "tipo_planilla", length = 2)
    private String tipoPlanilla;

    @Column(name = "numero_planilla", length = 30)
    private String numeroPlanilla;

    @Column(name = "numero_planilla_asociada", length = 30)
    private String numeroPlanillaAsociada;

    @Column(name = "fecha_pago_asociada")
    private LocalDate fechaPagoAsociada;

    @Column(name = "cod_arl", length = 10)
    private String codArl;

    /**
     * Pensión/ARL/CCF: mes VENCIDO.
     *
     * <p>Distinto de {@link #periodoPagoSalud}. No unificarlos: son períodos
     * distintos en el mismo archivo.
     */
    @Column(name = "periodo_pago", length = 7)
    private String periodoPago;

    /** Salud: mes ANTICIPADO. */
    @Column(name = "periodo_pago_salud", length = 7)
    private String periodoPagoSalud;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "total_empleados", nullable = false)
    private Integer totalEmpleados = 0;

    @Column(name = "total_nomina", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalNomina = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
