package com.cloud_technological.aura_pos.dto.caja;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCajaDto {
    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private final Boolean activa = true;
}
