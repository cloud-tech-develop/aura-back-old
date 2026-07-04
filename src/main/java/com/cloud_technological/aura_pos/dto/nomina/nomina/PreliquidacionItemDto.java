package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreliquidacionItemDto {
    private Long empleadoId;
    private String empleadoNombre;
    private BigDecimal salarioBase;
    private Integer diasTrabajados;
    private BigDecimal totalDevengado;
    private BigDecimal totalDeducciones;
    private BigDecimal netoPagar;
    private String estado;                 // estado de la nómina, o SIN_LIQUIDAR
    private List<String> alertas = new ArrayList<>();
}
