package com.cloud_technological.aura_pos.dto.proyecto;

import lombok.Data;

/** Lista plana para dropdowns. */
@Data
public class ProyectoDto {
    private Long id;
    private String codigo;
    private String nombre;
    private String estado;
}
