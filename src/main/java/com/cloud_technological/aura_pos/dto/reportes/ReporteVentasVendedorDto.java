package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteVentasVendedorDto {
    private Integer usuarioId;
    private String vendedorNombre;
    private Long totalVentas;
    private BigDecimal ingresos;
    private BigDecimal ticketPromedio;
    private BigDecimal descuentoTotal;
}
