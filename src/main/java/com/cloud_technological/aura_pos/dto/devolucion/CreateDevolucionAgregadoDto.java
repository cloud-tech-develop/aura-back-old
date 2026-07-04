package com.cloud_technological.aura_pos.dto.devolucion;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * Producto que el cliente se lleva en un cambio: se suma a la venta original.
 * El precio unitario viene SIN IVA y el impuesto es el valor total de IVA de la
 * línea (por la cantidad indicada).
 */
@Getter
@Setter
public class CreateDevolucionAgregadoDto {

    @NotNull(message = "El productoId es obligatorio")
    private Long productoId;

    private Long productoPresentacionId;

    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;

    /** Precio unitario SIN IVA. */
    @NotNull(message = "El precio unitario es obligatorio")
    private BigDecimal precioUnitario;

    /** Valor total de IVA de la línea (por la cantidad). */
    private BigDecimal impuestoValor = BigDecimal.ZERO;
}
