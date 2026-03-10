package com.cloud_technological.aura_pos.dto.unidad_medida;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUnidadMedidaDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    @NotBlank(message = "La abreviatura es obligatoria")
    private String abreviatura;
    private Boolean permiteDecimales = false;
    private Boolean activo = true;
}
