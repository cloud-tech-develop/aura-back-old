package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Series para las gráficas del informe de resultados del período. */
@Getter
@Setter
public class GraficasCierreDto {
    private String fechaDesde;
    private String fechaHasta;
    /** DIA o MES: los rangos largos se agrupan por mes para que la gráfica se lea. */
    private String granularidad;
    private List<PuntoCierreDto> serie = new ArrayList<>();
    /** Ventas del período por medio de pago. */
    private List<ParteCierreDto> mediosPago = new ArrayList<>();
    /** Gastos del período por categoría (top 6 y "Otros"). */
    private List<ParteCierreDto> gastosCategoria = new ArrayList<>();
}
