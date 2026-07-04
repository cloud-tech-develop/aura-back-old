package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsistenciaDiaDto {
    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private LocalDate fecha;
    private Long turnoId;
    private String turnoNombre;
    private LocalTime horaEntradaProgramada;
    private LocalTime horaSalidaProgramada;
    private LocalTime horaEntradaReal;
    private LocalTime horaSalidaReal;
    private Integer minutosProgramados;
    private Integer minutosTrabajados;
    private Integer minutosTarde;
    private Integer minutosSalidaTemprana;
    private Integer minutosExtraDiurna;
    private Integer minutosExtraNocturna;
    private Integer minutosDominicalFestiva;
    private Integer minutosNocturnos;
    private String estadoAsistencia;
    private String estadoAprobacion;
    private String observacion;
}
