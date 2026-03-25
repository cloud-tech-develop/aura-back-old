package com.cloud_technological.aura_pos.dto.nomina.periodo;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePeriodoNominaDto {
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}
