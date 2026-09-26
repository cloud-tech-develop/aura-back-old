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

    /** Bodega de la que sale o a la que entra. Sin ella, la principal de la sucursal. */
    private Long bodegaId;
    @NotBlank(message = "El serial es obligatorio")
    private String serial;
    private String estado = "DISPONIBLE";
}