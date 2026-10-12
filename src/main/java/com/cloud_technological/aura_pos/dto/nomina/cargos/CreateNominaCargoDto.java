package com.cloud_technological.aura_pos.dto.nomina.cargos;

import java.math.BigDecimal;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Crear o editar un cargo. */
@Getter
@Setter
public class CreateNominaCargoDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código admite hasta 20 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
    private String nombre;

    private Long nivelId;

    private Long tipoCargoId;

    @Min(value = 0, message = "Las plazas no pueden ser negativas")
    private Integer plazas;

    private BigDecimal salarioReferencia;

    private BigDecimal salarioMinimo;

    private BigDecimal salarioMaximo;

    private String mision;

    private String funciones;

    private Boolean activo = true;
}
