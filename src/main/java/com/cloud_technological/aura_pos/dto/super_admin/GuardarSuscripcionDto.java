package com.cloud_technological.aura_pos.dto.super_admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import lombok.Data;

/** Crea o actualiza la membresía de una empresa (upsert por empresa). */
@Data
public class GuardarSuscripcionDto {

    @NotNull(message = "El tipo de plan es obligatorio")
    private String tipoPlan;   // UNICO | MENSUAL

    private String estado;     // PRUEBA | ACTIVA | SUSPENDIDA | CANCELADA (default ACTIVA)
    private BigDecimal valor;
    private String moneda;
    private LocalDate fechaInicio;
    private LocalDate fechaProximoPago;
    private Integer diaCobro;
    private String contactoNombre;
    private String contactoEmail;
    private String contactoTelefono;
    private String notas;
}
