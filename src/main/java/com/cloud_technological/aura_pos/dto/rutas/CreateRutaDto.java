package com.cloud_technological.aura_pos.dto.rutas;

import java.util.List;

import lombok.Data;

@Data
public class CreateRutaDto {
    private Long vendedorId;
    private String nombre;
    private String descripcion;
    private List<RutaLocalDto> locales;
}