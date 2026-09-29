package com.cloud_technological.aura_pos.dto.centros_costos;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class CentroCostoTableDto {
    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String tipo;
    private Integer nivel;
    private Boolean permiteMovimientos;
    private BigDecimal presupuestoAsignado;
    private Boolean activo;
    private Long padreId;
    private String nombrePadre;
    private long totalRows;
}
