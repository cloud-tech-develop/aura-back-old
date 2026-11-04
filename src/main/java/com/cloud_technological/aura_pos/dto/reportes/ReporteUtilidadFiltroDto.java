package com.cloud_technological.aura_pos.dto.reportes;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Filtros del reporte de utilidad en ventas. */
@Getter
@Setter
public class ReporteUtilidadFiltroDto {

    /** Cómo se agrupan las filas. */
    public enum Agrupacion { PRODUCTO, CATEGORIA, VENDEDOR, CLIENTE, MES }

    /** Qué ventas entran según cómo se pagaron. */
    public enum Forma { TODAS, CONTADO, CREDITO }

    private LocalDate desde;
    private LocalDate hasta;
    private Agrupacion agrupar = Agrupacion.PRODUCTO;
    private Forma forma = Forma.TODAS;
}
