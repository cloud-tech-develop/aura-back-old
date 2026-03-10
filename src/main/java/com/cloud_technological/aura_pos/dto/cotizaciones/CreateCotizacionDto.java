package com.cloud_technological.aura_pos.dto.cotizaciones;

import java.util.List;

import javax.validation.constraints.NotEmpty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCotizacionDto {
    private Long terceroId;
    private Long turnoCajaId;
    private String observaciones;
    private Integer diasVigencia = 3;
    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateCotizacionDetalleDto> detalles;
}
