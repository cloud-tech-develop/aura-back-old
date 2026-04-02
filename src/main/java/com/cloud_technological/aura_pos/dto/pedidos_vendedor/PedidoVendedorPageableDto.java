package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import lombok.Data;

@Data
public class PedidoVendedorPageableDto {
    private Integer page;
    private Integer rows;
    private String search;
    private String estado;
    private String orderBy;
    private String order;
}
