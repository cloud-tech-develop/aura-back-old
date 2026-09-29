package com.cloud_technological.aura_pos.dto.laboral;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CalendarioDiaDto {
    private Long id;
    private LocalDate fecha;
    private String tipoDia;
    private String nombre;
    private Boolean aplicaRecargo;
    private Boolean esFestivoNacional;
    private Boolean esFestivoRegional;
    private Boolean esDescansoEmpresa;
    private String origen;
}
