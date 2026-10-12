package com.cloud_technological.aura_pos.dto.nomina.vinculacion;

import lombok.Getter;
import lombok.Setter;

/** Tipo de vinculación para tablas, detalle y selects. */
@Getter
@Setter
public class TipoVinculacionTableDto {
    private Long id;
    private String codigo;
    private String nombre;
    private String tipoContrato;
    private String tipoRemuneracion;
    private String frecuenciaPago;
    private Boolean exigeFechaFin;
    private Boolean unicaPorEmpleado;
    private String descripcion;
    private Boolean activo;
    private long totalRows;
}
