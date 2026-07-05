package com.cloud_technological.aura_pos.dto.super_admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/** Fila de la lista de clientes del platform (empresa + su membresía). */
@Data
public class ClienteDto {

    private Integer empresaId;
    private String razonSocial;
    private String nombreComercial;
    private String nit;
    private Boolean activa;

    private boolean tieneSuscripcion;
    private Long suscripcionId;
    private String tipoPlan;            // UNICO | MENSUAL
    private String estado;             // PRUEBA | ACTIVA | SUSPENDIDA | CANCELADA
    private String estadoEfectivo;     // igual a estado, pero VENCIDA si mensual y venció
    private boolean vencida;
    private Integer diasParaVencer;    // negativo si ya venció

    private BigDecimal valor;
    private String moneda;
    private LocalDate fechaInicio;
    private LocalDate fechaProximoPago;

    private String contactoNombre;
    private String contactoEmail;
    private String contactoTelefono;

    private BigDecimal totalPagado;
    private LocalDate ultimoPago;
}
