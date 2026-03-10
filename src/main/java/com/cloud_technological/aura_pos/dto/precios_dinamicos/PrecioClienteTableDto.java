package com.cloud_technological.aura_pos.dto.precios_dinamicos;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrecioClienteTableDto {
    private Long id;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private Long productoPresentacionId;
    private String productoPresentacionNombre;
    private String productoNombre;
    private BigDecimal precioEspecial;
    private Boolean activo;
    private Long totalRows;
}
