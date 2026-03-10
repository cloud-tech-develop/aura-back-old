package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaCategoriaDto {
    private Integer    categoriaId;
    private String     categoriaNombre;
    private Integer    totalProductosVendidos; // unidades vendidas
    private BigDecimal totalBruto;             // precio × cantidad sin descuentos
    private BigDecimal totalDescuentos;
    private BigDecimal totalNeto;   
}
