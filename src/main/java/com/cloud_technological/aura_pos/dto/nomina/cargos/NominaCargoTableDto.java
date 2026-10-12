package com.cloud_technological.aura_pos.dto.nomina.cargos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Cargo para tablas, detalle y selects (con nombres de nivel y tipo, y plazas ocupadas). */
@Getter
@Setter
public class NominaCargoTableDto {
    private Long id;
    private String codigo;
    private String nombre;
    private Long nivelId;
    private String nivelNombre;
    private Long tipoCargoId;
    private String tipoCargoNombre;
    private Integer plazas;
    /** Vinculaciones activas en el cargo (se llena cuando la vinculación use el catálogo, R2). */
    private Integer ocupadas;
    private BigDecimal salarioReferencia;
    private BigDecimal salarioMinimo;
    private BigDecimal salarioMaximo;
    private String mision;
    private String funciones;
    private Boolean activo;
    private long totalRows;
}
