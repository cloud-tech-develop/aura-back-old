package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddNovedadDto {
    private String tipo;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal valorUnitario;
}
