package com.cloud_technological.aura_pos.dto.nomina.prestacion;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearPrestacionDto {
    private Long empleadoId;
    private String tipo;          // PRIMA | VACACIONES
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private String observacion;
}
