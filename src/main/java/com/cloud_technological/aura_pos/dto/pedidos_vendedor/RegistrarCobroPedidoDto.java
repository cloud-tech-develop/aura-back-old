package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import javax.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class RegistrarCobroPedidoDto {

    @NotBlank
    private String metodoPago;

    private String referencia;
}
