package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateEmpleadoTurnoDto {
    private Long empleadoId;
    private Long turnoId;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;    // null = vigente indefinido
    private String diasSemana;     // ISO CSV, ej "1,2,3,4,5" (opcional)
}
