package com.cloud_technological.aura_pos.dto.producto_presentacion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoPresentacionTableDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private String nombre;
    private String codigoBarras;
    private BigDecimal factorConversion;
    private BigDecimal precio;
    private BigDecimal costo;
    private Boolean esDefaultCompra;
    private Boolean esDefaultVenta;
    private Boolean seVende;
    private Boolean activo;
    private long totalRows;
}
