package com.cloud_technological.aura_pos.dto.comision;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComisionLiquidacionDto {
    private Long id;
    private Integer empresaId;
    private Integer tecnicoId;
    private String tecnicoNombre;
    private String fechaDesde;
    private String fechaHasta;
    private Integer totalServicios;
    private BigDecimal valorTotal;
    private String estado;
    private String observaciones;
    private String fechaPago;
    private String createdAt;
    private List<ComisionVentaDto> detalles;
}
