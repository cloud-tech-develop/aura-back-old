package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NovedadResumenDto {
    private Long nominaId;
    private Long empleadoId;
    private String empleadoNombre;
    private String tipo;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private Boolean esDeduccion;
    private String origen;
}
