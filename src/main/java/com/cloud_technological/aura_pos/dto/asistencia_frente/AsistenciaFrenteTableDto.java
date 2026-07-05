package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AsistenciaFrenteTableDto {
    private Long id;
    private Long proyectoId;
    private String proyectoCodigo;
    private String proyectoNombre;
    private Long frenteId;
    private String frenteCodigo;
    private String frenteNombre;
    private String liderNombre;
    private LocalDate fecha;
    private String estado;
    private LocalDateTime enviadoRevisionAt;
    private long trabajadoresCount;
    private long alertasCriticas;
    private Long soportePdfId;
    private long totalRows;
}
