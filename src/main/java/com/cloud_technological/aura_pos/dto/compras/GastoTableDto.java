package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GastoTableDto {
    private Long id;
    private String categoria;
    private String descripcion;
    private BigDecimal monto;
    private LocalDate fecha;
    private Boolean deducible;
    private String estado;
    private String sucursalNombre;
    private String usuarioNombre;
    private Long totalRows;
}
