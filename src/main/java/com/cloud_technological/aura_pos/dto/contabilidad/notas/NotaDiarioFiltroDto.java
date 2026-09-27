package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import lombok.Getter;
import lombok.Setter;

/** Filtros de {@code POST /api/contabilidad/notas/page}. Todos opcionales. */
@Getter @Setter
public class NotaDiarioFiltroDto {
    /** yyyy-MM-dd */
    private String fechaDesde;
    /** yyyy-MM-dd */
    private String fechaHasta;
    /** BORRADOR | CONTABILIZADO | ANULADO */
    private String estado;
    private String clasificacion;
}
