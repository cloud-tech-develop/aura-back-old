package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Un abono dentro del estado de cuenta.
 *
 * <p>Lo que hace útil esta fila es {@link #cajaNombre}: hasta la parte B del
 * plan, un abono hecho desde un comprobante manual nacía sin turno y no había
 * forma de saber en qué caja entró la plata. Ahora se puede responder "¿quién
 * recibió este abono y dónde cayó?" sin salir del reporte.
 */
@Getter
@Setter
public class ReporteCarteraAbonoDto {

    private Long id;
    /** El documento al que pertenece; lo usa el agrupador. */
    private Long cuentaId;

    private LocalDateTime fechaPago;
    private BigDecimal monto;
    private String metodoPago;
    private String referencia;

    private Long turnoCajaId;
    private String cajaNombre;
    private String usuarioNombre;

    /** La plata se movió del cajón otro día: no entró a ningún arqueo vivo. */
    private Boolean cajaOtroDia;
}
