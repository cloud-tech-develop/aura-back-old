package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearIncidenciaDto {
    private Long empleadoId;
    private LocalDate fecha;
    private String tipoIncidencia;
    private String descripcion;
    private Boolean requiereSoporte;
    private String soporteUrl;
}
