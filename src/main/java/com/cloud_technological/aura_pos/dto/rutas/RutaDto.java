package com.cloud_technological.aura_pos.dto.rutas;

import lombok.Data;

@Data
public class RutaDto {
    private Long id;
    private Long empresaId;
    private Long vendedorId;
    private String vendedorNombre;
    private String nombre;
    private String descripcion;
    private Boolean activo;
}