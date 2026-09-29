package com.cloud_technological.aura_pos.dto.kardex;

import lombok.Getter;
import lombok.Setter;

/**
 * Filtros del reporte de movimiento de inventario.
 *
 * <p>Hereda los del listado y agrega cómo agrupar. El reporte y el listado
 * comparten filtros a propósito: el usuario filtra en la pantalla y exporta lo
 * que está viendo, no otra cosa.
 */
@Getter
@Setter
public class KardexReporteFiltroDto extends KardexFiltroDto {

    /** PRODUCTO | PRODUCTO_SUCURSAL | PRODUCTO_LOTE. */
    private String agrupacion = "PRODUCTO";

    public static final String POR_PRODUCTO = "PRODUCTO";
    public static final String POR_PRODUCTO_SUCURSAL = "PRODUCTO_SUCURSAL";
    public static final String POR_PRODUCTO_LOTE = "PRODUCTO_LOTE";
}
