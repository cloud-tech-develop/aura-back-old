package com.cloud_technological.aura_pos.dto.comision;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateComisionConfigDto {

    @NotNull(message = "El productoId es requerido")
    private Long productoId;

    private Integer tecnicoId; // nullable = se asigna en caja

    @NotNull(message = "El tipo es requerido")
    private String tipo; // PORCENTAJE | VALOR_FIJO

    @NotNull(message = "El porcentaje del técnico es requerido")
    @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a 0")
    @DecimalMax(value = "99.99", message = "El porcentaje debe ser menor a 100")
    private BigDecimal porcentajeTecnico;

    @NotNull(message = "El porcentaje del negocio es requerido")
    @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a 0")
    @DecimalMax(value = "99.99", message = "El porcentaje debe ser menor a 100")
    private BigDecimal porcentajeNegocio;

    private Boolean activo;
}
