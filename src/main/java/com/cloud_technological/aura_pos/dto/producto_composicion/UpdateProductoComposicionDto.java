package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProductoComposicionDto {
    /** Cantidad por lote, en la unidad indicada. */
    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.000001", message = "La cantidad debe ser mayor que cero")
    private BigDecimal cantidad;

    @NotBlank(message = "El tipo es obligatorio")
    private String tipo;

    private Long unidadMedidaId;

    private Long productoPresentacionId;

    @DecimalMin(value = "0.000001", message = "El factor de unidad debe ser mayor que cero")
    private BigDecimal factorUnidad;

    @DecimalMin(value = "0", message = "La merma no puede ser negativa")
    @DecimalMax(value = "99.99", message = "La merma no puede llegar al 100%")
    private BigDecimal mermaPorcentaje;

    private Integer orden;

    private String nota;
}
