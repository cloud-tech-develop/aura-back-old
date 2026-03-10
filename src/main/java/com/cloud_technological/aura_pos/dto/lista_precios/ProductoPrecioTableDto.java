package com.cloud_technological.aura_pos.dto.lista_precios;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoPrecioTableDto {
    private Long id;
    private Long listaPrecioId;
    private String listaPrecioNombre;
    private Long productoPresentacionId;
    private String productoPresentacionNombre;
    private String productoNombre;
    private BigDecimal precio;
    private BigDecimal utilidadEsperada;
    private long totalRows;
}
