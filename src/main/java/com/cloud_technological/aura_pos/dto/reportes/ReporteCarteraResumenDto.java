package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * El reporte de cartera completo: las filas por tercero más los totales.
 *
 * <p>Los totales se calculan sobre todo el filtro, no sobre la página: un pie
 * que suma solo lo visible es una cifra que no cuadra con nada.
 */
@Getter
@Setter
public class ReporteCarteraResumenDto {

    /** CXC | CXP — lo repite para que el front no tenga que recordarlo. */
    private String tipo;

    private List<ReporteCarteraTerceroDto> terceros = new ArrayList<>();

    private Integer documentos = 0;
    private Integer cantidadTerceros = 0;
    private BigDecimal totalDeuda = BigDecimal.ZERO;
    private BigDecimal totalAbonado = BigDecimal.ZERO;
    private BigDecimal saldoPendiente = BigDecimal.ZERO;

    private BigDecimal corriente = BigDecimal.ZERO;
    private BigDecimal mora1a30 = BigDecimal.ZERO;
    private BigDecimal mora31a60 = BigDecimal.ZERO;
    private BigDecimal mora61a90 = BigDecimal.ZERO;
    private BigDecimal moraMas90 = BigDecimal.ZERO;
}
