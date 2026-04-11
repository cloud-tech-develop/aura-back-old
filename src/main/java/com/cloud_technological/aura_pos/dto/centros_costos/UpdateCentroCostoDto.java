package com.cloud_technological.aura_pos.dto.centros_costos;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class UpdateCentroCostoDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código no puede superar 20 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    private String descripcion;

    private Long padreCentroCostoId;

    /** OPERATIVO | ADMINISTRATIVO | VENTAS | PRODUCCION | FINANCIERO */
    private String tipo;

    private Boolean permiteMovimientos;

    private BigDecimal presupuestoAsignado;

    private Long sucursalId;

    private Long responsableId;

    private Boolean activo;
}
