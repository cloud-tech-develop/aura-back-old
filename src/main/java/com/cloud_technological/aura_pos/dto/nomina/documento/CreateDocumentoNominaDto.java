package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Encabezado de una prenómina (crear o editar mientras está preliminar). */
@Getter
@Setter
public class CreateDocumentoNominaDto {

    @NotNull(message = "El tipo de comprobante es obligatorio")
    private Long tipoComprobanteId;

    @NotNull(message = "La fecha del documento es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "La fecha inicial del período es obligatoria")
    private LocalDate fechaDesde;

    @NotNull(message = "La fecha final del período es obligatoria")
    private LocalDate fechaHasta;

    /** Si no viene, se usa la fecha final del período. */
    private LocalDate fechaLiquidacion;

    private LocalDate fechaPago;

    private Boolean simulacion = false;

    @Size(max = 500, message = "La descripción admite hasta 500 caracteres")
    private String descripcion;
}
