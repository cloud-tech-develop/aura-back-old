package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteTopProductoDto {
    private Long productoId;
    private String productoNombre;
    private String sku;
    private String categoriaNombre;
    private BigDecimal cantidadVendida;
    private BigDecimal ingresos;
    private BigDecimal precioPromedio;
    private Long numeroPedidos;
}
