package com.cloud_technological.aura_pos.dto.comision;


import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateLiquidacionDto {

    @NotNull(message = "El tecnicoId es requerido")
    private Integer tecnicoId;

    @NotNull(message = "La fechaDesde es requerida")
    private String fechaDesde; // YYYY-MM-DD

    @NotNull(message = "La fechaHasta es requerida")
    private String fechaHasta; // YYYY-MM-DD

    private String observaciones;
}
