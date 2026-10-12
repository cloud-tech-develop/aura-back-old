package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Prenómina o nómina para el listado y el encabezado del documento. */
@Getter
@Setter
public class DocumentoNominaDto {
    private Long id;
    private Long periodoId;
    private Long tipoComprobanteId;
    private String tipoComprobanteNombre;
    /** Clase del tipo de comprobante: PERIODICO, PRIMA, CESANTIAS… */
    private String claseComprobante;
    /** PRENOMINA | NOMINA. */
    private String claseDocumento;
    private Integer numero;
    /** Número para mostrar: PRE-NOM-00001 (prenómina) o NOM-00001 (nómina). */
    private String numeroCompleto;
    private LocalDate fecha;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private LocalDate fechaLiquidacion;
    private LocalDate fechaPago;
    private Boolean simulacion;
    private String descripcion;
    /** PRELIMINAR | CONFIRMADO | ANULADO. */
    private String estado;
    private LocalDateTime fechaConfirmacion;
    private Long documentoOrigenId;
    private BigDecimal totalDevengado;
    private BigDecimal totalDeducido;
    private BigDecimal totalNeto;
    private BigDecimal totalAportes;
    /** Empleados (filas) del documento. */
    private Integer empleados;
    /** Nómina: empleados ya pagados. */
    private Integer pagados;
    /** Prenómina: la nómina no anulada que se generó desde ella. */
    private Long nominaGeneradaId;
    private String nominaGeneradaNumero;
    /** Nómina: número de la prenómina de la que salió. */
    private String documentoOrigenNumero;
    private long totalRows;
}
