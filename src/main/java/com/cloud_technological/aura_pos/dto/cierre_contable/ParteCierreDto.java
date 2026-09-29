package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un pedazo de un total: medio de pago, categoría de gasto… */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParteCierreDto {
    private String etiqueta;
    private BigDecimal valor;
    private Integer cantidad;
}
