package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurnoDto {
    private Long id;
    private String nombre;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Integer minutosDescanso;
    private Integer toleraLlegadaTardeMin;
    private Boolean cruzaMedianoche;
    private Boolean activo;
}
