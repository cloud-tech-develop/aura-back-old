package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class RegistrarCobroPedidoDto {

    @NotBlank
    @Size(max = 30, message = "El método de pago no puede superar 30 caracteres")
    private String metodoPago;

    @Size(max = 255, message = "La referencia no puede superar 255 caracteres")
    private String referencia;
}
