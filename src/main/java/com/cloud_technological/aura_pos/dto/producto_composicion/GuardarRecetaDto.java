package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Receta completa de un producto, para guardar de una sola vez.
 *
 * Reemplaza el total de las líneas del padre: lo que no venga en
 * {@link #componentes} se borra. Una lista vacía deja el producto sin receta.
 */
@Getter
@Setter
public class GuardarRecetaDto {

    @NotBlank(message = "El tipo es obligatorio")
    private String tipo; // KIT, RECETA

    /**
     * Unidades que salen del lote (ej: 40 panes). Null → se conserva el
     * rendimiento que ya tiene el producto (1 si nunca se fijó), para que un
     * cliente que no mande el campo no reescriba la receta sin querer.
     */
    @DecimalMin(value = "0.000001", message = "El rendimiento debe ser mayor que cero")
    private BigDecimal rendimiento;

    @NotNull(message = "Los componentes son obligatorios")
    @Valid
    private List<RecetaComponenteDto> componentes;
}
