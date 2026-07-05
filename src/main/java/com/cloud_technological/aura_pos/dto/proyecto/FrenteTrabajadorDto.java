package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class FrenteTrabajadorDto {
    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private String documento;
    private String cargo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
}
