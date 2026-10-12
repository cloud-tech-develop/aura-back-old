package com.cloud_technological.aura_pos.dto.nomina.vacaciones;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Registrar vacaciones de una vinculación. */
@Getter
@Setter
public class RegistrarVacacionDto {

    @NotNull(message = "Elija la vinculación")
    private Long contratoId;

    @NotBlank(message = "Elija si son en tiempo o en dinero")
    @Pattern(regexp = "TIEMPO|DINERO", message = "Tipo de vacaciones no válido")
    private String tipo;

    /** TIEMPO: primer día de disfrute; DINERO: fecha con la que entra a la nómina. */
    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    /** Último día de disfrute (obligatorio en TIEMPO). */
    private LocalDate fechaFin;

    /** Días compensados (obligatorio en DINERO). */
    private BigDecimal dias;

    @Size(max = 500, message = "La observación admite hasta 500 caracteres")
    private String observacion;
}
