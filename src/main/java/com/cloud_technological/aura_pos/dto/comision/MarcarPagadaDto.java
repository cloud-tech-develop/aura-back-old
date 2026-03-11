package com.cloud_technological.aura_pos.dto.comision;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarcarPagadaDto {

    @NotNull(message = "La fechaPago es requerida")
    private String fechaPago; // YYYY-MM-DD
}
