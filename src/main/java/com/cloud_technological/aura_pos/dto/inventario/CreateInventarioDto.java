package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInventarioDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;

    /** Bodega de la que sale o a la que entra. Sin ella, la principal de la sucursal. */
    private Long bodegaId;
    private BigDecimal stockMinimo = BigDecimal.ZERO;
    private BigDecimal stockActual = BigDecimal.ZERO;
    private String ubicacion;
}