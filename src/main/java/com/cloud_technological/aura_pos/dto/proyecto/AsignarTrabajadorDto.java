package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class AsignarTrabajadorDto {

    @NotNull(message = "El empleadoId es obligatorio")
    private Long empleadoId;

    private LocalDate fechaInicio;
    private String observacion;
}
