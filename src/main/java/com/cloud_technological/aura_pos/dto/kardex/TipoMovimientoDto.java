package com.cloud_technological.aura_pos.dto.kardex;

import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;

import lombok.Getter;
import lombok.Setter;

/**
 * Un tipo de movimiento del catálogo, tal como lo consume el filtro.
 *
 * <p>Se expone por API para que el front deje de mantener su propia lista: la
 * suya conocía 9 de los 17 tipos, así que merma, obsequio, devolución y
 * reconteo se veían en la tabla pero no se podían filtrar.
 */
@Getter
@Setter
public class TipoMovimientoDto {

    private String value;
    private String label;
    /** ENTRADA | SALIDA | MIXTO. */
    private String grupo;
    /** COMPRAS | VENTAS | DEVOLUCIONES | … — el desglose del reporte. */
    private String familia;

    public static TipoMovimientoDto de(TipoMovimientoInventario tipo) {
        TipoMovimientoDto dto = new TipoMovimientoDto();
        dto.setValue(tipo.codigo());
        dto.setLabel(tipo.etiqueta());
        dto.setGrupo(tipo.grupo().name());
        dto.setFamilia(tipo.familia().name());
        return dto;
    }
}
