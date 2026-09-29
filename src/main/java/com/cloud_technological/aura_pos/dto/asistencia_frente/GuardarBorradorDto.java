package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.time.LocalDate;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class GuardarBorradorDto {

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    private String observacionLider;

    @Valid
    private List<GuardarDetalleDto> detalles;
}
