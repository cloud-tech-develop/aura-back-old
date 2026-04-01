package com.cloud_technological.aura_pos.dto.permisos;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateModuloDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    
    @NotBlank(message = "El código es obligatorio")
    private String codigo;
    
    private String descripcion;
    private Integer orden;
}
