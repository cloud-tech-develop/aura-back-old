package com.cloud_technological.aura_pos.dto.laboral;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class JornadaConfigDto {
    private Long id;
    private LocalDate fechaInicioVigencia;
    private LocalDate fechaFinVigencia;
    private BigDecimal horasSemanalesLegales;
    private BigDecimal horasMensualesBase;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaDiurnaInicio;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaDiurnaFin;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaNocturnaInicio;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaNocturnaFin;

    private BigDecimal recargoNocturno;
    private BigDecimal recargoExtraDiurna;
    private BigDecimal recargoExtraNocturna;
    private BigDecimal recargoDominicalFestivo;
    private BigDecimal maxHorasExtraDia;
    private BigDecimal maxHorasExtraSemana;
    private Boolean aplicaExcepcionSectorial;
    private String sectorExcepcion;
}
