package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NominaNovedadDto {
    private Long id;
    private String tipo;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private Boolean esDeduccion;
}
