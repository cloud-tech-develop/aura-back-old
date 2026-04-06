package com.cloud_technological.aura_pos.dto.tipo_empleado;

import lombok.Data;

@Data
public class TipoEmpleadoDto {
    private Long id;
    private Long empresaId;
    private String nombre;
    private String descripcion;
    private Boolean activo;
}