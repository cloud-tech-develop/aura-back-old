package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class AsignarFrenteTurnoDto {

    @NotNull(message = "El turnoId es obligatorio")
    private Long turnoId;

    /** Desde cuándo aplica el turno (por defecto hoy). */
    private LocalDate fechaInicio;

    private LocalDate fechaFin;
}
