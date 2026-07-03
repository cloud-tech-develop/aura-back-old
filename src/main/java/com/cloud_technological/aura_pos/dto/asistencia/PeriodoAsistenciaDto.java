package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeriodoAsistenciaDto {
    private Long id;
    private Long periodoNominaId;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
}
