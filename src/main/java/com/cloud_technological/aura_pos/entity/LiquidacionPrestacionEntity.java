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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Liquidación de una prestación social (prima, vacaciones, ...) de un empleado.
 * El pago debita el pasivo por pagar (25xx) contra banco/caja.
 */
@Getter
@Setter
@Entity
@Table(name = "liquidacion_prestacion")
public class LiquidacionPrestacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private EmpleadoEntity empleado;

    /**
     * La prestación se liquida contra un CONTRATO (V112), no contra el empleado:
     * si hay dos vínculos, cada uno tiene su liquidación y su pasivo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id")
    private ContratoLaboralEntity contrato;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo; // PRIMA | VACACIONES | CESANTIAS | INTERESES_CESANTIAS | LIQUIDACION_DEFINITIVA | INDEMNIZACION

    /** Agrupa las filas de una misma liquidación (V122). "DEF-…" = definitiva. */
    @Column(name = "lote", length = 48)
    private String lote;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;

    @Column(name = "dias", nullable = false)
    private Integer dias = 0;

    @Column(name = "base_salarial", precision = 15, scale = 2, nullable = false)
    private BigDecimal baseSalarial = BigDecimal.ZERO;

    // ── V112 ────────────────────────────────────────────────────────────────

    /**
     * Base prestacional: <b>NO es el salario básico</b>.
     *
     * <p>Incluye auxilio de transporte + promedio de lo salarial variable del
     * período de referencia (horas extra, comisiones).
     *
     * <p><b>Es distinta del IBC (Fase 0) y de la base de retefuente (Fase 4.5).
     * Son TRES bases distintas.</b> Confundirlas es el error clásico.
     */
    @Column(name = "base_prestacional", precision = 15, scale = 2, nullable = false)
    private BigDecimal basePrestacional = BigDecimal.ZERO;

    @Column(name = "dias_liquidados", nullable = false)
    private Integer diasLiquidados = 0;

    /** Determina si hay indemnización y cómo se calcula. */
    @Column(name = "causa_retiro", length = 40)
    private String causaRetiro;

    /** Fondo al que se consignan las cesantías. Tercero con rol AFP. */
    @Column(name = "fondo_cesantias_id")
    private Long fondoCesantiasId;

    /** Si se liquidó dentro de una nómina, cuál. */
    @Column(name = "nomina_id")
    private Long nominaId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "traza", columnDefinition = "jsonb")
    private String traza;

    @Column(name = "valor", precision = 15, scale = 2, nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "BORRADOR"; // BORRADOR | APROBADA | PROGRAMADA | PAGADA | ANULADA

    @Column(name = "medio_pago", length = 20)
    private String medioPago; // EFECTIVO | TRANSFERENCIA

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "observacion", length = 255)
    private String observacion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
