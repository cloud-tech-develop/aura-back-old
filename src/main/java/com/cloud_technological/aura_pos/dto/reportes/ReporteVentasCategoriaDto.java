package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteVentasCategoriaDto {
    private Long categoriaId;
    private String categoriaNombre;
    private Long totalVentas;
    private BigDecimal totalUnidades;
    private BigDecimal ingresos;
    private BigDecimal costoEstimado;
    private BigDecimal margenBruto;
}
