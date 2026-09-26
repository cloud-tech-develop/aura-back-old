package com.cloud_technological.aura_pos.dto.bodegas;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBodegaDto {

    @NotNull(message = "La bodega debe pertenecer a una sucursal")
    private Integer sucursalId;

    @Size(max = 20, message = "El código no puede superar 20 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre de la bodega es obligatorio")
    @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
    private String nombre;

    private Integer responsableUsuarioId;

    private Boolean esPrincipal;

    private Boolean permiteVenta;

    @Size(max = 120)
    private String ubicacion;

    @Size(max = 300)
    private String observacion;
}
