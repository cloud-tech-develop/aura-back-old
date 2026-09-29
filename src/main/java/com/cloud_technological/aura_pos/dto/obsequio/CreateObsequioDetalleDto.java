package com.cloud_technological.aura_pos.dto.obsequio;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateObsequioDetalleDto {

    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    /** Si viene, cantidad y baseComercialUnitaria están en esa presentación (1 Paca). */
    private Long productoPresentacionId;

    private Long loteId;

    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;

    /** Seriales que salen, si el producto maneja serial: uno por unidad. */
    private java.util.List<Long> serialIds;

    /**
     * Valor comercial unitario SIN IVA sobre el que se causa el impuesto por
     * retiro. Si no viene, se deriva del precio de venta del producto. Se puede
     * enviar para casos donde el valor comercial no es el precio de lista.
     */
    private BigDecimal baseComercialUnitaria;
}
