package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Componente que sale del inventario por un producto con receta. Sirve para la
 * vista previa del formulario (con stock) y para el detalle del documento ya
 * registrado (sin stock).
 */
@Getter
@Setter
public class ConsumoComponenteDto {
    /** Línea del documento a la que pertenece; null en la vista previa. */
    private Long detalleId;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private String unidadAbreviatura;
    /** En unidad base de stock del componente. */
    private BigDecimal cantidad;
    private BigDecimal costoUnitario;
    private BigDecimal costoTotal;
    /** Solo en la vista previa. Null si el componente no tiene inventario en la sucursal. */
    private BigDecimal stockDisponible;
    private Boolean permitirStockNegativo;
    private Boolean suficiente;
}
