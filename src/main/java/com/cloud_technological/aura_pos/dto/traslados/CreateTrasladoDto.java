package com.cloud_technological.aura_pos.dto.traslados;

import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTrasladoDto {
    @NotNull(message = "La sucursal origen es obligatoria")
    private Long sucursalOrigenId;
    @NotNull(message = "La sucursal destino es obligatoria")
    private Long sucursalDestinoId;
    private String observacion;
    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateTrasladoDetalleDto> detalles;
}
