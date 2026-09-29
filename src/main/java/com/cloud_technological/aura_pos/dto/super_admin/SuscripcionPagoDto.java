package com.cloud_technological.aura_pos.dto.super_admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/** Un pago del historial de la suscripción. */
@Data
public class SuscripcionPagoDto {

    private Long id;
    private LocalDate fechaPago;
    private BigDecimal monto;
    private String metodo;
    private LocalDate periodoDesde;
    private LocalDate periodoHasta;
    private String referencia;
    private String observacion;
}
