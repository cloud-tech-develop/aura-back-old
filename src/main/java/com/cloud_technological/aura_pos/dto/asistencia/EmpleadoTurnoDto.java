package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpleadoTurnoDto {
    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private Long turnoId;
    private String turnoNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String diasSemana;
    private Boolean activo;
}
