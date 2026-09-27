package com.cloud_technological.aura_pos.dto.bodegas;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** La sucursal no se cambia: mover una bodega de sede movería el stock. */
@Getter
@Setter
public class UpdateBodegaDto {

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

    private Boolean activa;
}
