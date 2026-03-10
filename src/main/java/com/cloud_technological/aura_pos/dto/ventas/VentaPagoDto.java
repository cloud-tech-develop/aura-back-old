package com.cloud_technological.aura_pos.dto.ventas;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaPagoDto {
    private Long id;
    private String metodoPago;
    private BigDecimal monto;
    private String referencia;
}
