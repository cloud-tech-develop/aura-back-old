package com.cloud_technological.aura_pos.dto.precios_dinamicos;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePrecioClienteDto {
    private Long terceroId;
    private Long productoPresentacionId;
    private BigDecimal precioEspecial;
    private String fechaInicio;
    private String fechaFin;
    private Boolean activo;
    private String observaciones;
}
