package com.cloud_technological.aura_pos.dto.reconteo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconteoResponseDto {
    private Long id;
    private Integer sucursalId;
    private String sucursalNombre;
    private String estado;
    private String tipo;
    private String observaciones;
    private String creadoPorNombre;
    private String aprobadoPorNombre;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaCierre;
    private List<ReconteoDetalleResponseDto> detalles;
}
