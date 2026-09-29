package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTurnoDto {
    private String nombre;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Integer minutosDescanso;         // opcional, default 0
    private Integer toleraLlegadaTardeMin;   // opcional, default 0
    private Boolean cruzaMedianoche;         // opcional, default false
    private Boolean activo;                   // opcional, default true
}
