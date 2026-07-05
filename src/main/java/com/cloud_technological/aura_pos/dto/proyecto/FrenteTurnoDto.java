package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class FrenteTurnoDto {
    private Long id;
    private Long turnoId;
    private String turnoNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}
