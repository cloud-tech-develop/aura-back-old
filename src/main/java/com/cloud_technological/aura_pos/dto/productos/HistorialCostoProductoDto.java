package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Historial de costos de un producto: evolución del promedio y compras. */
@Getter
@Setter
public class HistorialCostoProductoDto {
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private String unidadAbreviatura;
    /** Costo promedio vigente. */
    private BigDecimal costoActual;
    private BigDecimal stockActual;
    /** Último costo neto de compra (sin anuladas ni notas crédito). */
    private BigDecimal ultimoCostoCompra;
    private BigDecimal costoCompraMinimo;
    private BigDecimal costoCompraMaximo;
    private List<CostoHistorialDto> cambios;
    private List<CompraProductoHistorialDto> compras;
}
