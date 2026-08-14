package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Alta de UNA línea de composición.
 *
 * Para editar una receta completa usar {@link GuardarRecetaDto}, que hace el
 * diff en una sola transacción.
 */
@Getter
@Setter
public class CreateProductoComposicionDto {
    @NotNull(message = "El producto padre es obligatorio")
    private Long productoPadreId;
    @NotNull(message = "El producto hijo es obligatorio")
    private Long productoHijoId;

    /**
     * Cantidad por lote, en la unidad indicada. Se conserva el nombre `cantidad`
     * por compatibilidad con los clientes que ya llamaban este endpoint: cuando
     * no se manda unidad ni presentación y el rendimiento es 1, significa
     * exactamente lo mismo que antes.
     */
    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.000001", message = "La cantidad debe ser mayor que cero")
    private BigDecimal cantidad;

    @NotBlank(message = "El tipo es obligatorio")
    private String tipo; // KIT, RECETA

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
