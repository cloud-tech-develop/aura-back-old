package com.cloud_technological.aura_pos.dto.cotizaciones;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCotizacionDetalleDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    private String descripcion;
    @NotNull(message = "La cantidad es obligatoria")
    private BigDecimal cantidad;
    @NotNull(message = "El precio es obligatorio")
    private BigDecimal precioUnitario;
    private BigDecimal ivaPorcentaje = BigDecimal.ZERO;
    private BigDecimal descuentoValor = BigDecimal.ZERO;
}
