package com.cloud_technological.aura_pos.dto.cartera.tablero;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Tablero gerencial de cartera: cómo está, cómo viene y quién la concentra. */
@Getter
@Setter
public class TableroCarteraDto {
    private TableroKpisDto kpis;
    private List<TableroMesDto> meses;
    private List<TableroDeudorDto> topDeudores;
    private List<TableroGrupoDto> porVendedor;
    private List<TableroGrupoDto> recaudoPorMedio;
}
