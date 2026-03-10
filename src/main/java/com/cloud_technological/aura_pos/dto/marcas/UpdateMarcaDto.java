package com.cloud_technological.aura_pos.dto.marcas;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMarcaDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private Boolean activo;

}
