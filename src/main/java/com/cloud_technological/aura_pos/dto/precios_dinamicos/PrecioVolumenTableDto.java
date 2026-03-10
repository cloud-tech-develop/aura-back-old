package com.cloud_technological.aura_pos.dto.precios_dinamicos;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrecioVolumenTableDto {
    private Long id;
    private Long productoPresentacionId;
    private String productoPresentacionNombre;
    private String productoNombre;
    private Integer cantidadMinima;
    private Integer cantidadMaxima;
    private BigDecimal precioUnitario;
    private Boolean activo;
    private Long totalRows;
}
