package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteResumenAvanzadoDto {
    private BigDecimal totalVentasPeriodo;
    private BigDecimal totalVentasPeriodoAnterior;
    private BigDecimal variacionVentas;
    private BigDecimal totalComprasPeriodo;
    private BigDecimal margenBrutoPeriodo;
    private Long cantidadTransacciones;
    private BigDecimal ticketPromedio;
    private Long clientesNuevos;
    private Long clientesRecurrentes;
    private String topCategoria;
    private String topProducto;
    private String topVendedor;
    private List<Map<String, Object>> ventasPorDia;
    private List<Map<String, Object>> ventasPorMetodoPago;
}
