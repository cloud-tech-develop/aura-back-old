package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteIvaDto {
    private String fechaDesde;
    private String fechaHasta;

    // IVA en ventas
    private BigDecimal ivaVentas       = BigDecimal.ZERO;
    private Integer    cantidadVentas  = 0;

    // IVA en compras
    private BigDecimal ivaCompras      = BigDecimal.ZERO;
    private Integer    cantidadCompras = 0;

    // Balance (IVA a pagar o a favor)
    private BigDecimal ivaADeclararOPagarAlEstado = BigDecimal.ZERO; // ivaVentas - ivaCompras
}
