package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Una fila de la proyección: cuota, acumulada y valor en libros de un mes futuro. */
@Getter
@AllArgsConstructor
public class ProyeccionDepreciacionDto {
    private String periodo;
    private BigDecimal cuota;
    private BigDecimal acumulada;
    private BigDecimal valorEnLibros;
}
