package com.cloud_technological.aura_pos.dto.centros_costos;

import lombok.Data;

@Data
public class CentroCostoDto {
    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private Boolean permiteMovimientos;
}
