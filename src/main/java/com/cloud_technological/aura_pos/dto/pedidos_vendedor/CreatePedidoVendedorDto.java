package com.cloud_technological.aura_pos.dto.pedidos_vendedor;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

import lombok.Data;

@Data
public class CreatePedidoVendedorDto {

    private Long clienteId;

    private String observaciones;

    @NotEmpty
    @Valid
    private List<CreatePedidoVendedorDetalleDto> detalles;

    /** Autorización del supervisor si el descuento pasa el límite del vendedor (V192). */
    private Long autorizacionId;
}
