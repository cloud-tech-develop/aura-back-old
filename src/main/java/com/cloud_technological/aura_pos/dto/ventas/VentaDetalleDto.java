package com.cloud_technological.aura_pos.dto.ventas;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private Long productoPresentacionId;
    private String presentacionNombre;
    private Long loteId;
    private String codigoLote;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal montoDescuento;
    private BigDecimal impuestoValor;
    private BigDecimal subtotalLinea;
    private String unidadMedidaNombre;
}
