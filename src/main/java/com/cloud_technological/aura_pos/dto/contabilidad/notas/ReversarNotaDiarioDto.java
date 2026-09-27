package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReversarNotaDiarioDto {

    /** Fecha de la reversión: debe caer en un período abierto. */
    @NotNull(message = "Indique la fecha de la reversión")
    private LocalDate fecha;

    /** Concepto opcional; por defecto "Reversión de CD-…". */
    @Size(max = 500, message = "El concepto admite máximo 500 caracteres")
    private String concepto;
}
