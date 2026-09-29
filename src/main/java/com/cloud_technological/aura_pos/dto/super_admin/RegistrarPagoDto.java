package com.cloud_technological.aura_pos.dto.super_admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import lombok.Data;

/** Registra un pago de la suscripción. Puede avanzar la fecha de próximo pago. */
@Data
public class RegistrarPagoDto {

    @NotNull(message = "La fecha de pago es obligatoria")
    private LocalDate fechaPago;

    @NotNull(message = "El monto es obligatorio")
    private BigDecimal monto;

    private String metodo;         // EFECTIVO | TRANSFERENCIA | TARJETA | PASARELA | OTRO
    private LocalDate periodoDesde;
    private LocalDate periodoHasta;
    private String referencia;
    private String observacion;

    /** Si es mensual, avanza fecha_proximo_pago (a periodo_hasta+1 o +1 mes). Default true. */
    private Boolean avanzarProximoPago = Boolean.TRUE;
}
