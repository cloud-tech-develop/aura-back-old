package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Una línea de la receta tal como la escribe el usuario.
 *
 * Ojo con {@link #cantidadReceta}: es la cantidad del LOTE completo, no la de
 * una unidad del producto. El servicio la divide por el rendimiento.
 */
@Getter
@Setter
public class RecetaComponenteDto {

    /** Id de la línea existente. Null = línea nueva. */
    private Long id;

    @NotNull(message = "El componente es obligatorio")
    private Long productoHijoId;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.000001", message = "La cantidad debe ser mayor que cero")
    private BigDecimal cantidadReceta;

    /** Unidad en la que está escrita la cantidad. Solo informativa. */
    private Long unidadMedidaId;

    /**
     * Presentación del componente de la que sale la equivalencia (ej: "bulto
     * 50 kg"). Si viene, manda sobre {@link #factorUnidad}.
     */
    private Long productoPresentacionId;

    /**
     * Unidades base de stock del componente que equivalen a 1 unidad escrita.
     * Se ignora si viene {@link #productoPresentacionId}. Null → 1.
     */
    @DecimalMin(value = "0.000001", message = "El factor de unidad debe ser mayor que cero")
    private BigDecimal factorUnidad;

    /** Merma del ingrediente en el proceso, en %. Null → 0. */
    @DecimalMin(value = "0", message = "La merma no puede ser negativa")
    @DecimalMax(value = "99.99", message = "La merma no puede llegar al 100%")
    private BigDecimal mermaPorcentaje;

    private Integer orden;

    private String nota;
}
