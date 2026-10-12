package com.cloud_technological.aura_pos.dto.nomina.retiro;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Retiro para el listado y el detalle. */
@Getter
@Setter
public class RetiroDto {
    private Long id;
    private Long contratoId;
    private String codigoVinculacion;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String cargo;
    private String tipoContrato;
    private LocalDate fechaInicio;
    private LocalDate fechaRetiro;
    private Long motivoId;
    private String motivo;
    private Boolean indemniza;
    private LocalDate fechaFinPactada;
    private LocalDate cesantiasCorteHasta;
    private String observacion;
    /** PENDIENTE | EN_LIQUIDACION | LIQUIDADO | PAGADO | ANULADO (deducido de la liquidación). */
    private String estado;
    /** Prenómina de liquidación definitiva. */
    private Long documentoId;
    private String documentoNumero;
    /** Nómina generada desde esa prenómina. */
    private Long nominaId;
    private String nominaNumero;
    private LocalDateTime createdAt;
    private long totalRows;
}
