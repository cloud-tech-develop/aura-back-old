package com.cloud_technological.aura_pos.dto.periodo_contable;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class AbrirPeriodoDto {

    @NotNull(message = "El año es obligatorio")
    @Min(value = 2000, message = "Año inválido")
    @Max(value = 2100, message = "Año inválido")
    private Integer anio;

    @NotNull(message = "El mes es obligatorio")
    @Min(value = 1, message = "El mes debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes debe estar entre 1 y 12")
    private Integer mes;

    private String observaciones;
}
