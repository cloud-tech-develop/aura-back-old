package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PedidoVendedorTableDto {
    private Long id;
    private String numeroPedido;
    private String vendedorNombre;
    private String clienteNombre;
    private String estado;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private long totalRows;
}
