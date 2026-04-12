package com.cloud_technological.aura_pos.dto.locales;

import lombok.Data;

@Data
public class UpdateLocalDto {
    private String nombre;
    private String direccion;
    private String ciudad;
    private Integer ciudadId;
    private String barrio;
    private Double latitud;
    private Double longitud;
    private String imagenFachada;
    private String horarioJson;
    private String preferenciaDiasJson;
    private Long vendedorActualId;
    private Boolean activo;
}