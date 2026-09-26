package com.cloud_technological.aura_pos.dto.merma;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMermaDetalleDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    /** Si viene, cantidad está en esa presentación (1 Paca); costoUnitario sigue por unidad base. */
    private Long productoPresentacionId;
    private Long loteId;
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;

    /** Seriales que salen, si el producto maneja serial: uno por unidad. */
    private java.util.List<Long> serialIds;
    @NotNull(message = "El costo unitario es obligatorio")
    private BigDecimal costoUnitario;
}
