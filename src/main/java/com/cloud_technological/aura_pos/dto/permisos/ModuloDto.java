package com.cloud_technological.aura_pos.dto.permisos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModuloDto {
    private Integer id;
    private String nombre;
    private String codigo;
    private String descripcion;
    private Boolean activo;
    private Integer orden;
}
