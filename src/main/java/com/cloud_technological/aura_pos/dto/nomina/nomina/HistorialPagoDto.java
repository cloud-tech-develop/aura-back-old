package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * Fila de la trazabilidad de pagos de un empleado: una nómina liquidada con su
 * período, montos, estado y datos del pago (medio y fecha).
 */
@Data
public class HistorialPagoDto {
    private Long id;
    private Long periodoId;
    private LocalDate periodoFechaInicio;
    private LocalDate periodoFechaFin;
    private Integer diasTrabajados;
    private BigDecimal totalDevengado;
    private BigDecimal totalDeducciones;
    private BigDecimal netoPagar;
    private String estado;
    private String medioPago;
    private LocalDateTime fechaPago;
}
