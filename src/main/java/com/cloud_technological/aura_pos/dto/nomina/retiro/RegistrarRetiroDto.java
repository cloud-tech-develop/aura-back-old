package com.cloud_technological.aura_pos.dto.nomina.retiro;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Registrar el retiro de una vinculación. */
@Getter
@Setter
public class RegistrarRetiroDto {

    @NotNull(message = "Elija la vinculación")
    private Long contratoId;

    @NotNull(message = "La fecha de retiro es obligatoria")
    private LocalDate fechaRetiro;

    @NotNull(message = "El motivo de retiro es obligatorio")
    private Long motivoId;

    /** Cesantías consignadas al fondo fuera del sistema hasta esta fecha (opcional). */
    private LocalDate cesantiasCorteHasta;

    @Size(max = 500, message = "La observación admite hasta 500 caracteres")
    private String observacion;
}
