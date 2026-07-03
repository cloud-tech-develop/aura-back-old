package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarcajeDto {
    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private LocalDate fecha;
    private LocalDateTime fechaHoraMarcaje;
    private String tipoMarcaje;
    private String origenMarcaje;
    private Integer registradoPor;
    private String observacion;
    private String evidenciaUrl;
    private String estado;
}
