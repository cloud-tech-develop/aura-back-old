package com.cloud_technological.aura_pos.dto.lista_precios;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateListaPreciosDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private Boolean activa = true;
}
