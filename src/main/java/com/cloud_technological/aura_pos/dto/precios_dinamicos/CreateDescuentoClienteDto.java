package com.cloud_technological.aura_pos.dto.precios_dinamicos;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDescuentoClienteDto {
    private Long terceroId;
    private Long categoriaId;
    private BigDecimal porcentajeDescuento;
    private String tipoDescuento;
    private String fechaInicio;
    private String fechaFin;
    private Boolean activo;
    private String observaciones;
}
