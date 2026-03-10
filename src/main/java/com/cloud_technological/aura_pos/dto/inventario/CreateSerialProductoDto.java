package com.cloud_technological.aura_pos.dto.inventario;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSerialProductoDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;
    @NotBlank(message = "El serial es obligatorio")
    private String serial;
    private String estado = "DISPONIBLE";
}