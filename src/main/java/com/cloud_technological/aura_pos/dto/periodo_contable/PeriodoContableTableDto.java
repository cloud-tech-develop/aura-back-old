package com.cloud_technological.aura_pos.dto.periodo_contable;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PeriodoContableTableDto {
    private Long id;
    private Short anio;
    private Short mes;
    private String estado;
    private LocalDate fechaApertura;
    private LocalDate fechaCierre;
    private String observaciones;
    private LocalDateTime createdAt;
    private Long totalAsientos;
}
