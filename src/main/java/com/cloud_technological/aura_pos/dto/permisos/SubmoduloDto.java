package com.cloud_technological.aura_pos.dto.permisos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmoduloDto {
    private Integer id;
    private Integer moduloId;
    private String moduloNombre;
    private String nombre;
    private String codigo;
    private String descripcion;
    private Boolean activo;
    private Integer orden;
}
