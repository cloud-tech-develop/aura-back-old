package com.cloud_technological.aura_pos.dto.rutas;

import java.util.List;

import lombok.Data;

@Data
public class UpdateRutaDto {
    private String nombre;
    private String descripcion;
    private Integer diaSemana;
    private Long vendedorId;
    private List<Long> localIds;
    private Boolean activo;
}