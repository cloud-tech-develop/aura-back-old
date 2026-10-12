package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Filtros del listado de prenóminas y nóminas (todos opcionales). */
@Getter
@Setter
public class FiltroDocumentoNominaDto {
    /** PRENOMINA | NOMINA. */
    private String claseDocumento;
    private Long tipoComprobanteId;
    /** PRELIMINAR | CONFIRMADO | ANULADO. */
    private String estado;
    /** Documentos cuyo período se cruza con [desde, hasta]. */
    private LocalDate desde;
    private LocalDate hasta;
}
