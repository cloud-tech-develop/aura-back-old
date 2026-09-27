package com.cloud_technological.aura_pos.dto.cartera.tablero;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Una fila agrupada: vendedor o medio de pago. */
@Getter
@Setter
public class TableroGrupoDto {
    private String nombre;
    private BigDecimal valor;
    private BigDecimal vencido;
    private Integer cantidad;
}
