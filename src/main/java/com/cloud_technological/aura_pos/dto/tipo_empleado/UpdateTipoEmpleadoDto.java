package com.cloud_technological.aura_pos.dto.tipo_empleado;

import lombok.Data;

@Data
public class UpdateTipoEmpleadoDto {
    private String nombre;
    private String descripcion;
    private Boolean activo;
}