package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraPagoDto {
    private Long id;
    private String metodoPago;
    private BigDecimal monto;
    private String banco;
}
