package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AnularNotaDiarioDto {

    @NotBlank(message = "Indique el motivo de la anulación")
    @Size(min = 5, max = 300, message = "El motivo debe tener entre 5 y 300 caracteres")
    private String motivo;
}
