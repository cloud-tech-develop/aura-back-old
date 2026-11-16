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
    /** Total cobrado con IVA. */
    private BigDecimal ingresos;
    /** Venta sin IVA, después de descuentos: la base de la utilidad. */
    private BigDecimal ventaNeta;
    private BigDecimal costoEstimado;
    private BigDecimal margenBruto;
}
