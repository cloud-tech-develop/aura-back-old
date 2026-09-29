package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * El reporte completo: las filas agrupadas más los totales del período.
 *
 * <p>Los totales van aparte y se calculan sobre <b>todo</b> el filtro, no sobre
 * la página: un pie de página que suma solo lo visible es una cifra que no
 * cuadra con nada y que alguien va a copiar a una declaración.
 */
@Getter
@Setter
public class ReporteGastosResumenDto {

    private List<ReporteGastosLineaDto> lineas = new ArrayList<>();

    private Integer cantidad = 0;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal totalDeducible = BigDecimal.ZERO;
    private BigDecimal totalNoDeducible = BigDecimal.ZERO;
    private BigDecimal totalContado = BigDecimal.ZERO;
    private BigDecimal totalCredito = BigDecimal.ZERO;
    private BigDecimal baseIva = BigDecimal.ZERO;
    private BigDecimal valorIva = BigDecimal.ZERO;
    private BigDecimal valorRetefuente = BigDecimal.ZERO;
    private BigDecimal valorReteica = BigDecimal.ZERO;
}
