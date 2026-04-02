package com.cloud_technological.aura_pos.dto.devolucion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DevolucionDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private Long productoPresentacionId;
    private Long loteId;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal impuestoValor;
    private BigDecimal subtotalLinea;
}
