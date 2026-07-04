package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearPeriodoAsistenciaDto {
    private Long periodoNominaId; // opcional
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}
