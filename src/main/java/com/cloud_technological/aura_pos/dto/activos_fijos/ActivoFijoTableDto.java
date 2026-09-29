package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivoFijoTableDto {
    private Long id;
    private String codigo;
    private String descripcion;
    private String categoria;
    private LocalDate fechaAdquisicion;
    private BigDecimal valorCompra;
    private BigDecimal depreciacionAcumulada;
    private BigDecimal valorEnLibros;
    private String metodoDepreciacion;
    private Integer vidaUtilMeses;
    private String estado;
    private Long totalRows;
}
