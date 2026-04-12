package com.cloud_technological.aura_pos.dto.tipo_empleado;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTipoEmpleadoDto {

    @NotBlank(message = "El nombre es requerido")
    private String nombre;
    private String descripcion;
}
