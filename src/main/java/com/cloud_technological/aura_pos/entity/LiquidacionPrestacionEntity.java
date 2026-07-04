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

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo; // PRIMA | VACACIONES

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;

    @Column(name = "dias", nullable = false)
    private Integer dias = 0;

    @Column(name = "base_salarial", precision = 15, scale = 2, nullable = false)
    private BigDecimal baseSalarial = BigDecimal.ZERO;

    @Column(name = "valor", precision = 15, scale = 2, nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "BORRADOR"; // BORRADOR | APROBADA | PAGADA | ANULADA

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
