package com.cloud_technological.aura_pos.dto.nomina.vacaciones;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** Regreso anticipado de unas vacaciones en tiempo. */
@Getter
@Setter
public class RetornoAnticipadoDto {

    /** Día en que volvió a trabajar. */
    @NotNull(message = "La fecha de regreso es obligatoria")
    private LocalDate fecha;
}
