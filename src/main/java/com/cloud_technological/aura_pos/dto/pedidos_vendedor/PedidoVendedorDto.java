package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class PedidoVendedorDto {
    private Long id;
    private String numeroPedido;
    private String vendedorNombre;
    private Long clienteId;
    private String clienteNombre;
    private String estado;
    private BigDecimal subtotal;
    private BigDecimal descuentoTotal;
    private BigDecimal impuestoTotal;
    private BigDecimal total;
    private String observaciones;
    private String metodoPago;
    private String referenciaPago;
    private LocalDateTime fechaCobro;
    private LocalDateTime createdAt;
    private Long ventaId;
    private List<PedidoVendedorDetalleDto> detalles;
}
