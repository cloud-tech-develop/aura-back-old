package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AutorizacionDto {
    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private Long periodoNominaId;
    private String motivo;
    private String observacion;
    private String estado;
    private LocalDateTime fechaAutorizacion;
}
