package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Encabezado de una prenómina o una nómina (V215). Se crea con un tipo de
 * comprobante, que define qué liquida y su numeración. Las filas de cada empleado
 * son {@link NominaEntity} con {@code documentoId} apuntando aquí.
 */
@Entity
@Table(name = "nomina_documento")
@Getter
@Setter
public class NominaDocumentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    /** Rango de fechas que leen el motor, PILA y la nómina electrónica. */
    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(name = "tipo_comprobante_id", nullable = false)
    private Long tipoComprobanteId;

    /** PRENOMINA | NOMINA. */
    @Column(name = "clase_documento", nullable = false, length = 12)
    private String claseDocumento;

    /** Consecutivo por (tipo de comprobante, clase de documento). */
    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Período de días trabajados que se liquida. */
    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;

    /** Fecha con la que se buscan los conceptos y tarifas vigentes. */
    @Column(name = "fecha_liquidacion", nullable = false)
    private LocalDate fechaLiquidacion;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    /** Una simulación nunca se confirma ni afecta nada. */
    @Column(nullable = false)
    private Boolean simulacion = Boolean.FALSE;

    @Column(length = 500)
    private String descripcion;

    /** PRELIMINAR | CONFIRMADO | ANULADO. */
    @Column(nullable = false, length = 12)
    private String estado = "PRELIMINAR";

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    /** Nómina generada desde una prenómina: apunta a la prenómina. */
    @Column(name = "documento_origen_id")
    private Long documentoOrigenId;

    @Column(name = "total_devengado", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDevengado = BigDecimal.ZERO;

    @Column(name = "total_deducido", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDeducido = BigDecimal.ZERO;

    @Column(name = "total_neto", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalNeto = BigDecimal.ZERO;

    @Column(name = "total_aportes", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAportes = BigDecimal.ZERO;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
