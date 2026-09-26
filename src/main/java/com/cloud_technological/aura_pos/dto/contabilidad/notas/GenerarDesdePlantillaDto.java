package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class GenerarDesdePlantillaDto {

    @NotNull(message = "Indique la fecha de la nota")
    private LocalDate fecha;
}
