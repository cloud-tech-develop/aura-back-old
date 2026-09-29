package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidenciaDto {
    private Long id;
    private Long asistenciaDiaId;
    private Long empleadoId;
    private String empleadoNombre;
    private LocalDate fecha;
    private String tipoIncidencia;
    private String descripcion;
    private String estado;
    private Boolean requiereSoporte;
    private String soporteUrl;
    private Integer revisadoPor;
    private LocalDateTime fechaRevision;
    private String observacionRevision;
}
