package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private Long productoPresentacionId;
    private String presentacionNombre;
    private BigDecimal presentacionFactor;
    private BigDecimal cantidadPresentacion;
    private BigDecimal costoPresentacion;
    private Long loteId;
    private String codigoLote;
    private BigDecimal cantidad;
    private BigDecimal costoUnitario;
    private BigDecimal impuestoValor;
    private BigDecimal subtotalLinea;
    private BigDecimal descuentoPct;
    private BigDecimal descuentoValor;
    private BigDecimal precioVenta1;
    private BigDecimal precioVenta2;
    private BigDecimal precioVenta3;
    private Boolean manejaLotes;
    private Boolean manejaSerial;
    /** Seriales que entraron con la línea (compra) o salieron (nota crédito). */
    private java.util.List<String> seriales = new java.util.ArrayList<>();
    private java.util.List<Long> serialIds = new java.util.ArrayList<>();
    private String unidadAbreviatura;
    private java.util.List<CompraDetalleLoteDto> lotes = new java.util.ArrayList<>();
}
