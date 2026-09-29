package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class NotaDiarioPlantillaDto {
    private Long id;
    private String nombre;
    private String descripcion;
    private String clasificacion;
    private Boolean recurrente;
    private Short diaMes;
    private String ultimoPeriodo;
    private Boolean activa;
    private Integer cantidadLineas;
    private BigDecimal totalDebito;
    private BigDecimal totalCredito;
    private List<NotaDiarioLineaDto> lineas;
}
