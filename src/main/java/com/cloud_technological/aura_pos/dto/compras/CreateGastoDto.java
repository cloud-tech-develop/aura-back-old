package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGastoDto {

    @NotNull(message = "La sucursal es obligatoria")
    private Integer sucursalId;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;

    private String descripcion;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    private LocalDate fecha;

    @NotNull(message = "Debe indicar si el gasto es deducible")
    private Boolean deducible;
}
