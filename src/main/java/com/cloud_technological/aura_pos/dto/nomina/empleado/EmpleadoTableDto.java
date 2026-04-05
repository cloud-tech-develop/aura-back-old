package com.cloud_technological.aura_pos.dto.nomina.empleado;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpleadoTableDto {
    private Long id;
    private String nombres;
    private String apellidos;
    private String nombreCompleto;
    private String tipoDocumento;
    private String numeroDocumento;
    private String cargo;
    private LocalDate fechaIngreso;
    private BigDecimal salarioBase;
    private String tipoContrato;
    private Boolean activo;
    private long totalRows;
    
    // ID del usuario vinculado (nullable)
    private Integer usuarioId;
}
