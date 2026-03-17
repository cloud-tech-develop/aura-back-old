package com.cloud_technological.aura_pos.dto.reconteo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconteoTableDto {
    private Long id;
    private String sucursalNombre;
    private String estado;
    private String tipo;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaCierre;
    private int totalProductos;
    private long diferenciasEncontradas;
    private long totalRows;
}
