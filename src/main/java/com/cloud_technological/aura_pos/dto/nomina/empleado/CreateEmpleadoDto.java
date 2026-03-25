package com.cloud_technological.aura_pos.dto.nomina.empleado;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateEmpleadoDto {
    private String nombres;
    private String apellidos;
    private String tipoDocumento; // CC | CE | PASAPORTE | NIT
    private String numeroDocumento;
    private String cargo;
    private LocalDate fechaIngreso;
    private BigDecimal salarioBase;
    private String tipoContrato; // INDEFINIDO | FIJO | OBRA_LABOR | PRESTACION_SERVICIOS
    private String banco;
    private String numeroCuenta;
    private String tipoCuenta; // AHORROS | CORRIENTE
    private Integer nivelRiesgoArl; // 1 al 5 (opcional, default 1)
}
