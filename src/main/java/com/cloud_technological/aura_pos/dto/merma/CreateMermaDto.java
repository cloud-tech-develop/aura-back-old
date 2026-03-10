package com.cloud_technological.aura_pos.dto.merma;

import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMermaDto {
    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;
    @NotNull(message = "El motivo es obligatorio")
    private Long motivoId;
    private String observacion;
    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateMermaDetalleDto> detalles;
}
