package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadCreditoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFiltroDto;

/**
 * Utilidad en ventas con el costo promedio congelado al vender, contado y crédito.
 */
public interface ReporteUtilidadService {

    /**
     * Totales y filas agrupadas del período.
     *
     * @param filtro período (por defecto el mes en curso), agrupación y forma de pago
     * @return reporte con márgenes calculados
     */
    ReporteUtilidadDto consultar(ReporteUtilidadFiltroDto filtro);

    /**
     * El mismo reporte en Excel.
     *
     * @param filtro período, agrupación y forma de pago
     * @return bytes del .xlsx
     */
    byte[] excel(ReporteUtilidadFiltroDto filtro);

    /**
     * Las ventas a crédito del período con su utilidad y lo que falta por cobrar.
     *
     * @param filtro período (por defecto el mes en curso)
     * @return una fila por venta, primero las que más deben
     */
    List<ReporteUtilidadCreditoDto> ventasCredito(ReporteUtilidadFiltroDto filtro);
}
