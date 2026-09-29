package com.cloud_technological.aura_pos.dto.cartera.tablero;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableroDeudorDto {
    private Long terceroId;
    private String terceroNombre;
    private String numeroDocumento;
    private Integer facturas;
    private BigDecimal saldo;
    private BigDecimal vencido;
    private Integer diasMoraMaximo;
    private Integer scoreCrediticio;
    private BigDecimal pctTotal;
}
