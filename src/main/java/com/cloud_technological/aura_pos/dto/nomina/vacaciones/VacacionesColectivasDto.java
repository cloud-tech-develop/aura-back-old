package com.cloud_technological.aura_pos.dto.nomina.vacaciones;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Vacaciones colectivas en tiempo: las mismas fechas para varias vinculaciones. */
@Getter
@Setter
public class VacacionesColectivasDto {

    /** Vinculaciones; vacío = todas las activas. */
    private List<Long> contratoIds = new ArrayList<>();

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate fechaFin;

    @Size(max = 500, message = "La observación admite hasta 500 caracteres")
    private String observacion;
}
