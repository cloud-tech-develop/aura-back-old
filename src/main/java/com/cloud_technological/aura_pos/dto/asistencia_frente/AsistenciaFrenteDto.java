package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class AsistenciaFrenteDto {
    private Long id;
    private Long proyectoId;
    private Long frenteId;
    private String frenteNombre;
    private LocalDate fecha;
    private String estado;
    private Long liderId;
    private String observacionLider;
    private Long soportePdfId;
    private String soportePdfUrl;
    private String soportePdfNombre;
    private String soportePdfSubidoPor;
    private java.time.LocalDateTime soportePdfSubidoAt;
    private List<AsistenciaDetalleDto> detalles;
    private List<AsistenciaAlertaDto> alertas;
}
