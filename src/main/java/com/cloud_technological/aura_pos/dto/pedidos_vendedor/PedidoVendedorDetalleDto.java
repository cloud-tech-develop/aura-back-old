package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PedidoVendedorDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal descuentoValor;
    private BigDecimal impuestoValor;
    private BigDecimal subtotalLinea;
}
