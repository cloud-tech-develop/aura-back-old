package com.cloud_technological.aura_pos.dto.visitas;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class VisitaTableDto {
    private Long id;
    private String localNombre;
    private String localDireccion;
    private String vendedorNombre;
    private String rutaNombre;
    private LocalDateTime fechaProgramada;
    private String horaProgramada;
    private String estado;
    private Integer totalRows;
}