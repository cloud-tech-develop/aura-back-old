package com.cloud_technological.aura_pos.dto.rutas;

import lombok.Data;

@Data
public class RutaTableDto {
    private Long id;
    private String nombre;
    private String vendedorNombre;
    private Integer cantidadLocales;
    private Boolean activo;
    private Integer totalRows;
}