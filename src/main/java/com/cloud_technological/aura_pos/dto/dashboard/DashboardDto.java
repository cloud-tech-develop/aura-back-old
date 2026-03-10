package com.cloud_technological.aura_pos.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardDto {
    private ResumenVentasDto ventasHoy;
    private ResumenVentasDto ventasMes;
    private BigDecimal totalComprasMes;
    private List<ProductoStockBajoDto> stockBajo;
    private List<LoteVencimientoDto> lotesProximosVencer;
    private List<VentaRecienteDto> ultimasVentas;
    private List<TopProductoDto> topProductos;
    private List<MovimientoRecienteDto> ultimosMovimientos;
}
