package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class PreliquidacionFrenteItemDto {
    private Long empleadoId;
    private String empleadoNombre;
    private String documento;
    private String proyectoNombre;
    private String frenteNombre;
    private LocalDate fecha;
    private String estadoAsistencia;
    private BigDecimal horasTrabajadas;
    private BigDecimal horasExtra;
    private String estadoFrente;
    /** Tipo(s) de novedad de nómina generada(s) desde este día, o null si fue jornada normal. */
    private String novedadGenerada;
}
