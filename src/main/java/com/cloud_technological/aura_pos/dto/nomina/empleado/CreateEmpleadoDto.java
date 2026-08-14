package com.cloud_technological.aura_pos.dto.nomina.empleado;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateEmpleadoDto {

    /**
     * Identidad de la persona (V99). Opcional durante la transición:
     * si viene, se enlaza ese tercero; si no, se busca o se crea uno a partir
     * del documento. Ver EmpleadoServiceImpl.resolverTercero().
     */
    private Long terceroId;

    /** @deprecated El nombre vive en `tercero`. Se usa solo para crearlo si no existe. */
    @Deprecated
    private String nombres;
    /** @deprecated Ídem. */
    @Deprecated
    private String apellidos;

    // Identificación desagregada: si se crea el tercero, se puebla bien de una.
    private String nombre1;
    private String nombre2;
    private String apellido1;
    private String apellido2;

    private String tipoDocumento; // CC | CE | PASAPORTE | NIT
    private String numeroDocumento;
    private String cargo;
    private LocalDate fechaIngreso;
    private LocalDate fechaFinContrato; // requerido cuando tipoContrato = FIJO
    private BigDecimal salarioBase;
    private String tipoContrato; // INDEFINIDO | FIJO | OBRA_LABOR | PRESTACION_SERVICIOS
    private String banco;
    private String numeroCuenta;
    private String tipoCuenta; // AHORROS | CORRIENTE
    private Integer nivelRiesgoArl; // 1 al 5 (opcional, default 1)
    private Boolean requiereControlAsistencia; // opcional, default false
}
