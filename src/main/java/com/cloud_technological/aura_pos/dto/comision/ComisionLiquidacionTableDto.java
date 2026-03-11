package com.cloud_technological.aura_pos.dto.comision;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComisionLiquidacionTableDto {
    private Long id;
    private String tecnicoNombre;
    private String fechaDesde;
    private String fechaHasta;
    private Integer totalServicios;
    private BigDecimal valorTotal;
    private String estado;
    private String fechaPago;
    private Long totalRows;
}
