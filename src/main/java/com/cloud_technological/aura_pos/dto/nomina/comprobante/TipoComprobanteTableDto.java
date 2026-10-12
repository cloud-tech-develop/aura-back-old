package com.cloud_technological.aura_pos.dto.nomina.comprobante;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Tipo de comprobante de nómina para tablas, detalle y selects. */
@Getter
@Setter
public class TipoComprobanteTableDto {
    private Long id;
    private String codigo;
    private String nombre;
    private String clase;
    private String prefijo;
    private String frecuencia;
    private String descripcion;
    private Boolean activo;
    /** Tipos de vinculación admitidos; vacío = todos. */
    private List<Long> tiposVinculacionIds = new ArrayList<>();
    /** Sus nombres separados por coma, para la tabla. */
    private String tiposVinculacionNombres;
    private long totalRows;
}
