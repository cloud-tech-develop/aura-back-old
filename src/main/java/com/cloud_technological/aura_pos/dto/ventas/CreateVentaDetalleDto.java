package com.cloud_technological.aura_pos.dto.ventas;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateVentaDetalleDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    private Long productoPresentacionId;
    private Long loteId;
    private List<Long> serialIds; // si maneja serial
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;
    @NotNull(message = "El precio es obligatorio")
    private BigDecimal precioUnitario;
    private Long reglaDescuentoId;
    private BigDecimal descuentoValor = BigDecimal.ZERO;
    private BigDecimal impuestoValor = BigDecimal.ZERO; // IVA u otro impuesto específico
}
