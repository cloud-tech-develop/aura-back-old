package com.cloud_technological.aura_pos.dto.nomina.periodo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeriodoNominaDto {
    private Long id;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
    private LocalDateTime createdAt;
}
