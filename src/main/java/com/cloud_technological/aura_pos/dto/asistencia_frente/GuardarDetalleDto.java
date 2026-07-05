package com.cloud_technological.aura_pos.dto.asistencia_frente;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class GuardarDetalleDto {

    @NotNull(message = "El empleadoId es obligatorio")
    private Long empleadoId;

    /** Formato "HH:mm". */
    private String horaEntrada;
    private String horaSalida;
    private String estadoAsistencia;
    private String observacionLider;
}
