package com.cloud_technological.aura_pos.dto.cotizaciones;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CotizacionDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal ivaPorcentaje;
    private BigDecimal descuentoValor;
    private BigDecimal subtotal;
}
