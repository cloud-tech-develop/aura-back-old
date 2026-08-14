package com.cloud_technological.aura_pos.dto.nomina.empleado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpleadoDto {
    private Long id;
    /** Identidad del empleado (V99). Para editar sus datos vía el form de tercero. */
    private Long terceroId;
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private String cargo;
    private LocalDate fechaIngreso;
    private LocalDate fechaRetiro;
    private LocalDate fechaFinContrato;
    private BigDecimal salarioBase;
    private String tipoContrato;
    private String banco;
    private String numeroCuenta;
    private String tipoCuenta;
    private Boolean activo;
    private Boolean requiereControlAsistencia;
    private Integer nivelRiesgoArl;
    private BigDecimal porcentajeArl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // ID del usuario vinculado (nullable)
    private Integer usuarioId;
}
