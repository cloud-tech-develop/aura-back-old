package com.cloud_technological.aura_pos.dto.traslado_fondos;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Una línea de la relación de gastos de un fondo (caja menor). */
@Getter
@Setter
public class MovimientoFondoDto {
    private LocalDate fecha;
    private String numeroComprobante;
    private String tipoOrigen;
    private String descripcion;
    private String tercero;
    private BigDecimal debito;
    private BigDecimal credito;
}
