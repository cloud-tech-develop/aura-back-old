package com.cloud_technological.aura_pos.dto.traslado_fondos;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnularTrasladoFondosDto {

    @NotBlank(message = "Indique por qué se anula el traslado")
    @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
    private String motivo;
}
